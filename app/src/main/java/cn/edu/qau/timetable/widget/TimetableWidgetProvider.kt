package cn.edu.qau.timetable.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import cn.edu.qau.timetable.MainActivity
import cn.edu.qau.timetable.QauApp
import cn.edu.qau.timetable.R
import cn.edu.qau.timetable.core.DayOverride
import cn.edu.qau.timetable.core.DayOverrides
import cn.edu.qau.timetable.core.PeriodTimes
import cn.edu.qau.timetable.core.Term
import cn.edu.qau.timetable.core.UiStyle
import cn.edu.qau.timetable.domain.CourseEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 桌面小组件：显示今天的课，今天没课时提示最近的一次课。
 * 用经典 RemoteViews 而不是 Glance —— 少一个依赖，构建更稳。
 */
class TimetableWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                render(context, appWidgetManager, appWidgetIds)
            } catch (_: Throwable) {
                // 小组件渲染失败不应该影响系统
            } finally {
                pending.finish()
            }
        }
    }

    companion object {

        private val WEEKDAY = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        /** 数据变化后主动刷新所有实例。 */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TimetableWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { render(context, manager, ids) }
            }
        }

        private suspend fun render(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
        ) {
            val app = context.applicationContext as? QauApp ?: return
            val repo = app.container.repo
            val settings = repo.settingsFlow.first()

            val views = RemoteViews(context.packageName, R.layout.widget_timetable)
            applyStyle(context, views, settings.uiStyle)

            val term = repo.activeTerm()
            if (term == null) {
                views.setTextViewText(R.id.widget_title, "青农课表")
                setBadge(views, null)
                views.setTextViewText(R.id.widget_body, "还没有课表数据\n打开 App 同步一次即可")
                bindClick(context, views)
                ids.forEach { manager.updateAppWidget(it, views) }
                return
            }

            val campus = term.campus
            val courses = repo.coursesNow(
                repo.activeTermEntity()?.id ?: -1L
            )

            val overrides = settings.dayOverrides

            val today = LocalDate.now()
            val week = term.weekOf(today)
            val ownDow = today.dayOfWeek.value
            val override = DayOverrides.of(today, overrides)
            // 调休：补课日按被借的那天的课表；放假日没有课（dow == null）
            val dow = DayOverrides.effectiveDayOfWeek(today, overrides)

            // 周次从标题里挪到右上角的徽标，标题只保留「青农课表 · 周三」，
            // 这样窄小组件时标题不会被挤到省略号。
            // 调休日把「补/休」也写进标题，否则用户会以为课表画错了。
            val title = buildString {
                append("青农课表 · ").append(WEEKDAY[ownDow - 1])
                val src = override?.useDayOfWeek
                when {
                    override == null -> Unit
                    override.isHoliday -> append("（放假）")
                    src != null -> append("（补").append(WEEKDAY[src - 1]).append("）")
                }
            }

            val todayCourses = if (week > 0 && dow != null) {
                courses.filter { it.dayOfWeek == dow && it.occursIn(week) }
                    .sortedBy { it.startPeriod }
            } else {
                emptyList()
            }

            val body = when {
                week <= 0 ->
                    "不在学期周次内\n（可在设置里调整开学日期）"

                dow == null ->
                    "今天放假 🎉\n（调休安排里标成了放假日）"

                todayCourses.isNotEmpty() ->
                    todayCourses.joinToString("\n") { line(it, campus) }

                else -> {
                    val upcoming = nextCourses(term, courses, today, overrides)
                    if (upcoming == null) {
                        "今天没课 🎉"
                    } else {
                        val (dayLabel, list) = upcoming
                        "今天没课 🎉\n\n$dayLabel：\n" + list.joinToString("\n") { line(it, campus) }
                    }
                }
            }

            views.setTextViewText(R.id.widget_title, title)
            setBadge(views, if (week > 0) "第 $week 周" else null)
            views.setTextViewText(R.id.widget_body, body)
            bindClick(context, views)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun line(course: CourseEvent, campus: cn.edu.qau.timetable.core.Campus): String {
            val periods = if (course.startPeriod == course.endPeriod) {
                "${course.startPeriod}节"
            } else {
                "${course.startPeriod}-${course.endPeriod}节"
            }
            val time = PeriodTimes.rangeText(campus, course.startPeriod, course.endPeriod)
            return buildString {
                append(periods).append(" ").append(course.name)
                if (course.room.isNotEmpty()) append("\n    ").append(course.room)
                if (time.isNotEmpty()) append("  ").append(time)
            }
        }

        /**
         * 往后找最近一天有课的。
         *
         * 按**日期**逐天推进，而不是按星期递推 —— 调休会把某天的课挪到别的星期，
         * 只有知道具体是哪一天，才算得出那天到底上哪一套课表、是不是放假。
         */
        private fun nextCourses(
            term: Term,
            courses: List<CourseEvent>,
            from: LocalDate,
            overrides: List<DayOverride>,
        ): Pair<String, List<CourseEvent>>? {
            for (step in 1..7) {
                val date = from.plusDays(step.toLong())
                val week = term.weekOf(date)
                if (week !in 1..term.totalWeeks) continue
                val day = DayOverrides.effectiveDayOfWeek(date, overrides) ?: continue
                val list = courses.filter { it.dayOfWeek == day && it.occursIn(week) }
                    .sortedBy { it.startPeriod }
                if (list.isNotEmpty()) {
                    val label = if (step == 1) "明天" else WEEKDAY[date.dayOfWeek.value - 1]
                    return label to list
                }
            }
            return null
        }

        /**
         * 按界面风格给小组件上色。
         *
         * 小组件走 RemoteViews，读不到 Compose 的 MaterialTheme；而资源限定符
         * （-night / -v31 …）又没法按 **App 内的设置** 切换。所以这里显式换背景
         * drawable、改文字颜色 —— 否则用户切到 MIUI X，桌面小组件还是一片农大绿。
         *
         * 默认风格什么都不做：布局里的静态资源色本来就是 M3 那套。
         * 深浅色仍然由 values-night 自动生效，这里只管风格。
         */
        private fun applyStyle(context: Context, views: RemoteViews, style: UiStyle) {
            if (style != UiStyle.MIUIX) return
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_miuix)
            views.setInt(
                R.id.widget_badge,
                "setBackgroundResource",
                R.drawable.widget_badge_bg_miuix,
            )
            views.setTextColor(R.id.widget_title, context.getColor(R.color.widget_on_surface_miuix))
            views.setTextColor(
                R.id.widget_body,
                context.getColor(R.color.widget_on_surface_variant_miuix),
            )
            views.setTextColor(
                R.id.widget_badge,
                context.getColor(R.color.widget_on_accent_container_miuix),
            )
        }

        /** 右上角的周次徽标；没有周次（无数据 / 不在学期周次内）时整个隐藏。 */
        private fun setBadge(views: RemoteViews, text: String?) {
            if (text.isNullOrEmpty()) {
                views.setViewVisibility(R.id.widget_badge, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_badge, View.VISIBLE)
                views.setTextViewText(R.id.widget_badge, text)
            }
        }

        private fun bindClick(context: Context, views: RemoteViews) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            // 绑在根布局上：整块都能点开 App。
            // 之前只绑了标题和正文两个 TextView，空白区域点了没反应。
            views.setOnClickPendingIntent(R.id.widget_root, pending)
        }
    }
}
