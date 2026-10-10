package cn.edu.qau.timetable.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.DayOverride
import cn.edu.qau.timetable.core.DayOverrides
import cn.edu.qau.timetable.core.GlassEffect
import cn.edu.qau.timetable.core.SilenceMode
import cn.edu.qau.timetable.core.UiStyle
import cn.edu.qau.timetable.data.model.ClassroomEntity
import cn.edu.qau.timetable.data.model.ExamEntity
import cn.edu.qau.timetable.data.model.GradeEntity
import cn.edu.qau.timetable.data.model.TermEntity
import cn.edu.qau.timetable.data.prefs.AppSettings
import cn.edu.qau.timetable.data.qz.QzPayload
import cn.edu.qau.timetable.data.repo.TimetableRepository
import cn.edu.qau.timetable.domain.CourseEvent
import cn.edu.qau.timetable.notify.ReminderScheduler
import cn.edu.qau.timetable.notify.SilenceScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repo: TimetableRepository,
    private val reminders: ReminderScheduler,
    private val silence: SilenceScheduler,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        repo.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val activeTerm: StateFlow<TermEntity?> =
        repo.observeActiveTerm().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val courses: StateFlow<List<CourseEvent>> =
        repo.observeActiveTerm()
            .flatMapLatest { t ->
                if (t == null) flowOf(emptyList()) else repo.observeCourses(t.id)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val exams: StateFlow<List<ExamEntity>> =
        repo.observeActiveTerm()
            .flatMapLatest { t ->
                if (t == null) flowOf(emptyList()) else repo.observeExams(t.id)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val grades: StateFlow<List<GradeEntity>> =
        repo.observeGrades().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val classrooms: StateFlow<List<ClassroomEntity>> =
        repo.observeClassrooms().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 0 表示「跟随当前周」。 */
    private val _weekOverride = MutableStateFlow(0)
    val weekOverride: StateFlow<Int> = _weekOverride.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun clearToast() { _toast.value = null }

    // ---------------------------------------------------------------- 周次

    /** 学校当前是第几周（依据学期第 1 周周一推算）。 */
    fun currentWeek(): Int {
        val t = activeTerm.value ?: return 0
        val term = t.toDomain()
        val w = term.weekOf(LocalDate.now())
        return w.coerceAtLeast(0)
    }

    val effectiveWeek: Int
        get() = _weekOverride.value.takeIf { it > 0 } ?: currentWeek().coerceAtLeast(1)

    fun setWeek(week: Int) { _weekOverride.value = week.coerceAtLeast(0) }

    fun stepWeek(delta: Int) { setWeek((effectiveWeek + delta).coerceIn(1, 30)) }

    // ---------------------------------------------------------------- 动作

    /**
     * 统一的兜底 launch。
     *
     * `viewModelScope` 里未捕获的异常会一路冒到线程的未捕获处理器，
     * **直接杀掉进程**。所有异步动作都必须走这里。
     */
    private fun safeLaunch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (t: Throwable) {
                _toast.value = "操作失败：${t.javaClass.simpleName}" +
                    (t.message?.let { ": $it" } ?: "")
            }
        }
    }

    fun setCampus(c: Campus) = safeLaunch {
        repo.settings.setCampus(c)
        reminders.reschedule()
    }

    fun setTerm(name: String, startMonday: String, totalWeeks: Int) = safeLaunch {
        repo.settings.setTerm(name, startMonday, totalWeeks)
        // 「第 1 周周一」决定 weekOf() 的结果：改完这一项，
        // 哪天有课整个变了，提醒和静音都得重排。
        reminders.reschedule()
        silence.reschedule()
        _toast.value = "学期已更新"
    }

    fun setRemind(enabled: Boolean, minutesBefore: Int) = safeLaunch {
        repo.settings.setRemind(enabled, minutesBefore)
        reminders.reschedule()
        _toast.value = if (enabled) "已开启上课提醒" else "已关闭上课提醒"
    }

    fun setUrls(kb: String, exam: String, grade: String, classroom: String) = safeLaunch {
        repo.settings.setUrls(kb, exam, grade, classroom)
        _toast.value = "抓取地址已保存"
    }

    /**
     * 上课自动静音。
     *
     * 关掉时不能只改设置：如果此刻正被我们静音着，必须立刻还原铃声，
     * 否则手机会一直哑到下一个闹钟（而闹钟已经被取消了）。
     */
    fun setSilence(enabled: Boolean, mode: SilenceMode) = safeLaunch {
        repo.settings.setSilence(enabled, mode)
        if (enabled) {
            silence.reschedule()
            _toast.value = "已开启上课自动静音（${mode.label}）"
        } else {
            silence.stop()
            _toast.value = "已关闭上课自动静音"
        }
    }

    /** 整份替换调休安排。改完必须重排提醒和静音 —— 补课日 / 放假日直接决定哪天上什么课。 */
    fun setDayOverrides(list: List<DayOverride>) = safeLaunch {
        repo.settings.setDayOverrides(list)
        reminders.reschedule()
        silence.reschedule()
        _toast.value = if (list.isEmpty()) "已清空调休安排" else "调休安排已更新（${list.size} 条）"
    }

    /** 悬浮底栏 / 原版底栏切换。 */
    fun setFloatingBottomBar(enabled: Boolean) = safeLaunch {
        repo.settings.setFloatingBottomBar(enabled)
        _toast.value = if (enabled) "已切到悬浮底栏" else "已切回原版底栏"
    }

    /** 底栏的玻璃效果：无 / 高斯模糊。 */
    fun setGlassEffect(effect: GlassEffect) = safeLaunch {
        repo.settings.setGlassEffect(effect)
        _toast.value = "底栏效果：${effect.label}"
    }

    /** Material You 动态取色。只对 Material 3 风格生效。 */
    fun setDynamicColor(enabled: Boolean) = safeLaunch {
        repo.settings.setDynamicColor(enabled)
        _toast.value = if (enabled) "已跟随壁纸取色" else "已用回内置配色"
    }

    /** 界面风格切换：只换配色 / 圆角 / 字体，界面结构不动。 */
    fun setUiStyle(style: UiStyle) = safeLaunch {
        repo.settings.setUiStyle(style)
        // 小组件的颜色是渲染时套上去的，不重画就还停在旧风格
        repo.refreshWidget()
        _toast.value = "界面风格：${style.label}"
    }

    /** 抓取完成后的入库。 */
    fun ingest(payload: QzPayload, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            // viewModelScope 里的异常如果没有被捕获，会直接杀掉进程。
            // 所有 launch 体都必须自带兜底。
            val result = try {
                repo.ingest(payload, settings.value)
            } catch (t: Throwable) {
                cn.edu.qau.timetable.data.repo.TimetableRepository.IngestResult(
                    0,
                    "内部错误：${t.javaClass.simpleName}${t.message?.let { m -> ": $m" } ?: ""}",
                )
            }
            val msg = if (result.ok) "已导入 ${result.count} 条数据" else result.message
            if (result.ok) {
                runCatching { reminders.reschedule() }
                // 新课表意味着新的上课时段，静音窗口要跟着重算
                runCatching { silence.reschedule() }
            }
            _toast.value = msg
            runCatching { onDone(result.ok, msg) }
        }
    }

    fun toast(msg: String) { _toast.value = msg }

    fun todayCourses(week: Int = currentWeek()): List<CourseEvent> {
        if (week <= 0) return emptyList()
        // 调休：补课日按被借的那天的课表；放假日直接没有课
        val day = DayOverrides.effectiveDayOfWeek(LocalDate.now(), settings.value.dayOverrides)
            ?: return emptyList()
        return courses.value
            .filter { it.dayOfWeek == day && it.occursIn(week) }
            .sortedBy { it.startPeriod }
    }
}
