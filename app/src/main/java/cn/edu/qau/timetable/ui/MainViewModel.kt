package cn.edu.qau.timetable.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.data.model.ClassroomEntity
import cn.edu.qau.timetable.data.model.ExamEntity
import cn.edu.qau.timetable.data.model.GradeEntity
import cn.edu.qau.timetable.data.model.TermEntity
import cn.edu.qau.timetable.data.prefs.AppSettings
import cn.edu.qau.timetable.data.qz.QzPayload
import cn.edu.qau.timetable.data.repo.TimetableRepository
import cn.edu.qau.timetable.domain.CourseEvent
import cn.edu.qau.timetable.notify.ReminderScheduler
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

    /** 强制登录页 LTR，规避 WebView 方向漂移导致的输入倒序。 */
    fun setForceLtr(enabled: Boolean) = safeLaunch {
        repo.settings.setForceLtr(enabled)
        _toast.value = if (enabled) "已开启输入方向修复" else "已关闭输入方向修复"
    }

    /** Material You 动态取色。 */
    fun setDynamicColor(enabled: Boolean) = safeLaunch {
        repo.settings.setDynamicColor(enabled)
        _toast.value = if (enabled) "已跟随壁纸取色" else "已用回内置配色"
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
            }
            _toast.value = msg
            runCatching { onDone(result.ok, msg) }
        }
    }

    fun toast(msg: String) { _toast.value = msg }

    fun todayCourses(week: Int = currentWeek()): List<CourseEvent> {
        if (week <= 0) return emptyList()
        val dow = LocalDate.now().dayOfWeek.value // 1..7，周一=1
        return courses.value
            .filter { it.dayOfWeek == dow && it.occursIn(week) }
            .sortedBy { it.startPeriod }
    }
}
