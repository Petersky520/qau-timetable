package cn.edu.qau.timetable.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import cn.edu.qau.timetable.MainActivity
import cn.edu.qau.timetable.QauApp
import cn.edu.qau.timetable.R
import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.DayOverrides
import cn.edu.qau.timetable.core.PeriodTimes
import cn.edu.qau.timetable.core.Term
import cn.edu.qau.timetable.data.prefs.AppSettings
import cn.edu.qau.timetable.domain.CourseEvent
import cn.edu.qau.timetable.domain.coursesOn
import cn.edu.qau.timetable.domain.nextCourseDay
import cn.edu.qau.timetable.ui.theme.qauColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import kotlin.math.max

class TimetableWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, manager, ids)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        update(context, manager, intArrayOf(appWidgetId))
    }

    private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                render(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val renderLock = Mutex()
        private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TimetableWidgetProvider::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { render(context, manager, ids) }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            try {
                renderLock.withLock {
                    val app = context.applicationContext as? QauApp ?: return
                    val repo = app.container.repo
                    val settings = repo.settingsFlow.first()
                    val active = repo.activeTermEntity()
                    val term = active?.toDomain()
                    val courses = if (active != null) repo.coursesNow(active.id) else emptyList()
                    val agenda = agendaFor(LocalDate.now(), term, courses, settings)
                    val campus = term?.campus ?: settings.campus
                    val light = qauColors(context, settings.dynamicColor, darkTheme = false)
                    val dark = qauColors(context, settings.dynamicColor, darkTheme = true)
                    val openApp = PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java).apply {
                            action = MainActivity.ACTION_SHOW_TODAY
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    val fontScale = max(1f, context.resources.configuration.fontScale)
                    fun viewsForHeight(height: Int): RemoteViews {
                        val rows = ((height / fontScale - 57) / 48).toInt().coerceIn(1, 12)
                        return widgetViews(context, agenda, campus, rows, openApp, light, dark)
                    }
                    ids.forEach { id ->
                        val options = manager.getAppWidgetOptions(id)
                        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
                        val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight)
                        val landscape = viewsForHeight(minHeight)
                        val views = if (maxHeight > minHeight) {
                            RemoteViews(landscape, viewsForHeight(maxHeight))
                        } else {
                            landscape
                        }
                        manager.updateAppWidget(id, views)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("TimetableWidget", "Widget update failed", error)
            }
        }

        private fun widgetViews(
            context: Context,
            agenda: Agenda,
            campus: Campus,
            maxRows: Int,
            openApp: PendingIntent,
            light: ColorScheme,
            dark: ColorScheme,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_timetable)
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            views.setTextViewText(R.id.widget_title, agenda.title)
            val date = agenda.date
            val dateText = "${date.monthValue}/${date.dayOfMonth} ${weekdays[date.dayOfWeek.value - 1]}"
            val courseCount = agenda.courses.size
            val courseSummary = when {
                courseCount == 0 -> null
                courseCount > maxRows -> "$maxRows/$courseCount 门课"
                else -> "$courseCount 门课"
            }
            val summary = listOfNotNull(dateText, agenda.note, courseSummary).joinToString(" · ")
            views.setTextViewText(R.id.widget_subtitle, summary)
            views.setViewVisibility(R.id.widget_badge, if (agenda.week > 0) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.widget_badge, "第 ${agenda.week} 周")
            views.setViewVisibility(R.id.widget_message, if (agenda.message == null) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.widget_message, agenda.message.orEmpty())

            views.removeAllViews(R.id.widget_courses)
            agenda.courses.take(maxRows).forEach { course ->
                val row = RemoteViews(context.packageName, R.layout.widget_course_row)
                val start = PeriodTimes.get(campus, course.startPeriod)
                val end = PeriodTimes.get(campus, course.endPeriod) ?: start
                val periods = if (course.startPeriod == course.endPeriod) {
                    "${course.startPeriod} 节"
                } else {
                    "${course.startPeriod}–${course.endPeriod} 节"
                }
                row.setTextViewText(R.id.widget_course_start, start?.startText ?: periods)
                row.setTextViewText(R.id.widget_course_end, end?.endText.orEmpty())
                row.setViewVisibility(R.id.widget_course_end, if (end == null) View.GONE else View.VISIBLE)
                row.setTextViewText(R.id.widget_course_name, course.name)
                row.setTextViewText(
                    R.id.widget_course_details,
                    listOf(course.room, periods).filter(String::isNotBlank).joinToString(" · "),
                )
                row.setOnClickPendingIntent(R.id.widget_course_row, openApp)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    row.setColorInt(R.id.widget_course_name, "setTextColor", light.onSurface.toArgb(), dark.onSurface.toArgb())
                    row.setColorInt(R.id.widget_course_details, "setTextColor", light.onSurfaceVariant.toArgb(), dark.onSurfaceVariant.toArgb())
                    row.setColorInt(R.id.widget_course_start, "setTextColor", light.primary.toArgb(), dark.primary.toArgb())
                    row.setColorInt(R.id.widget_course_end, "setTextColor", light.onSurfaceVariant.toArgb(), dark.onSurfaceVariant.toArgb())
                    row.setColorStateList(
                        R.id.widget_course_row, "setBackgroundTintList",
                        ColorStateList.valueOf(light.surfaceContainerLowest.toArgb()),
                        ColorStateList.valueOf(dark.surfaceContainer.toArgb()),
                    )
                }
                views.addView(R.id.widget_courses, row)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorInt(R.id.widget_title, "setTextColor", light.onSurface.toArgb(), dark.onSurface.toArgb())
                views.setColorInt(R.id.widget_subtitle, "setTextColor", light.onSurfaceVariant.toArgb(), dark.onSurfaceVariant.toArgb())
                views.setColorInt(R.id.widget_message, "setTextColor", light.onSurfaceVariant.toArgb(), dark.onSurfaceVariant.toArgb())
                views.setColorInt(R.id.widget_badge, "setTextColor", light.onPrimaryContainer.toArgb(), dark.onPrimaryContainer.toArgb())
                views.setColorStateList(
                    R.id.widget_root, "setBackgroundTintList",
                    ColorStateList.valueOf(light.surfaceContainerHigh.toArgb()),
                    ColorStateList.valueOf(dark.surfaceContainerHigh.toArgb()),
                )
                views.setColorStateList(
                    R.id.widget_badge, "setBackgroundTintList",
                    ColorStateList.valueOf(light.primaryContainer.toArgb()),
                    ColorStateList.valueOf(dark.primaryContainer.toArgb()),
                )
            }
            return views
        }

        private fun agendaFor(today: LocalDate, term: Term?, courses: List<CourseEvent>, settings: AppSettings): Agenda {
            if (courses.isEmpty()) return Agenda("今日课程", today, message = "打开青小农同步课表")
            if (term == null) return Agenda("今日课程", today, message = "请在设置中确认开学日期")

            val todayCourses = term.coursesOn(today, courses, settings.dayOverrides)
            val week = term.weekOf(today)
            val override = DayOverrides.of(today, settings.dayOverrides)
            if (todayCourses.isNotEmpty()) {
                val note = override?.useDayOfWeek?.let { "补${DayOverrides.labelOf(it)}" }
                return Agenda("今日课程", today, week, todayCourses, note = note)
            }

            val holiday = override?.isHoliday == true
            val title = when {
                holiday -> "今天放假"
                week == 0 -> "下次上课"
                else -> "今天没课"
            }
            val upcoming = term.nextCourseDay(today, courses, settings.dayOverrides)
            if (upcoming != null) {
                val (date, list) = upcoming
                val note = if (title == "下次上课") null else "下次"
                return Agenda(title, date, term.weekOf(date), list, note = note)
            }
            val ended = today >= term.startMonday.plusWeeks(term.totalWeeks.toLong())
            val message = if (ended) "本学期已结束" else "本学期暂无后续课程"
            return Agenda(if (title == "下次上课") "今日课程" else title, today, week, message = message)
        }

        private data class Agenda(
            val title: String,
            val date: LocalDate,
            val week: Int = 0,
            val courses: List<CourseEvent> = emptyList(),
            val note: String? = null,
            val message: String? = null,
        )
    }
}
