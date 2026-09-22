package cn.edu.qau.timetable.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import cn.edu.qau.timetable.MainActivity
import cn.edu.qau.timetable.QauApp
import cn.edu.qau.timetable.R
import cn.edu.qau.timetable.core.PeriodTimes
import cn.edu.qau.timetable.domain.CourseEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

            val views = RemoteViews(context.packageName, R.layout.widget_timetable)

            val term = repo.activeTerm()
            if (term == null) {
                views.setTextViewText(R.id.widget_title, "青农课表")
                views.setTextViewText(R.id.widget_body, "还没有课表数据\n打开 App 同步一次即可")
                bindClick(context, views)
                ids.forEach { manager.updateAppWidget(it, views) }
                return
            }

            val campus = term.campus
            val courses = repo.coursesNow(
                repo.activeTermEntity()?.id ?: -1L
            )

            val today = LocalDate.now()
            val week = term.weekOf(today)
            val dow = today.dayOfWeek.value

            val title = buildString {
                append("青农课表")
                if (week > 0) append(" · 第 ").append(week).append(" 周")
                append(" ").append(WEEKDAY[(dow - 1).coerceIn(0, 6)])
            }

            val todayCourses = if (week > 0) {
                courses.filter { it.dayOfWeek == dow && it.occursIn(week) }
                    .sortedBy { it.startPeriod }
            } else {
                emptyList()
            }

            val body = when {
                week <= 0 ->
                    "不在学期周次内\n（可在设置里调整开学日期）"

                todayCourses.isNotEmpty() ->
                    todayCourses.joinToString("\n") { line(it, campus) }

                else -> {
                    val upcoming = nextCourses(courses, week, dow)
                    if (upcoming == null) {
                        "今天没课 🎉"
                    } else {
                        val (dayLabel, list) = upcoming
                        "今天没课 🎉\n\n$dayLabel：\n" + list.joinToString("\n") { line(it, campus) }
                    }
                }
            }

            views.setTextViewText(R.id.widget_title, title)
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

        /** 往后找最近一天有课的。 */
        private fun nextCourses(
            courses: List<CourseEvent>,
            week: Int,
            todayDow: Int,
        ): Pair<String, List<CourseEvent>>? {
            for (step in 1..7) {
                val d = ((todayDow - 1 + step) % 7) + 1
                val list = courses.filter { it.dayOfWeek == d && it.occursIn(week) }
                    .sortedBy { it.startPeriod }
                if (list.isNotEmpty()) {
                    val label = if (step == 1) "明天" else WEEKDAY[d - 1]
                    return label to list
                }
            }
            return null
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
            views.setOnClickPendingIntent(R.id.widget_title, pending)
            views.setOnClickPendingIntent(R.id.widget_body, pending)
        }
    }
}
