package cn.edu.qau.timetable.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.DayOverride
import cn.edu.qau.timetable.core.DayOverrides
import cn.edu.qau.timetable.core.GlassEffect
import cn.edu.qau.timetable.core.SilenceMode
import cn.edu.qau.timetable.core.UiStyle
import cn.edu.qau.timetable.data.model.ExamEntity
import cn.edu.qau.timetable.data.model.GradeEntity
import cn.edu.qau.timetable.data.model.TermEntity
import cn.edu.qau.timetable.data.prefs.AppSettings
import cn.edu.qau.timetable.data.qz.QzPayload
import cn.edu.qau.timetable.data.repo.KaoyanRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repo: TimetableRepository,
    private val reminders: ReminderScheduler,
    private val silence: SilenceScheduler,
    /**
     * 考研模块的仓库。
     *
     * 这个类只借它做一件事：设置页「AI 搜索」卡片的保存与回读。
     * 考研页面的运行状态（意向画像、推荐结果、问答）全部归 `KaoyanViewModel`，
     * 不要往这里搬 —— 两个 ViewModel 各持一份同一批数据只会互相打架。
     */
    private val kaoyan: KaoyanRepository,
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

    /** 0 表示「跟随当前周」。 */
    private val _weekOverride = MutableStateFlow(0)
    val weekOverride: StateFlow<Int> = _weekOverride.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun clearToast() { _toast.value = null }

    init {
        // 升级自愈：2.1 之前「学期」设置只写 DataStore，数据库里那份
        // 还是首次导入课表时猜的日期。启动时对齐一次，用户什么都不用点。
        viewModelScope.launch {
            runCatching {
                if (repo.syncTermFromSettings()) {
                    reminders.reschedule()
                    silence.reschedule()
                }
            }
        }
    }

    // ---------------------------------------------------------------- 周次

    /**
     * 学校当前是第几周（依据学期第 1 周周一推算）。
     *
     * 返回 0 表示「算不出来」：没导入过课表、没填第 1 周周一、
     * 或者日期落在学期之外。调用方据此显示「不在学期周次内」。
     */
    fun currentWeek(): Int {
        val t = activeTerm.value ?: return 0
        val term = t.toDomain() ?: return 0
        return term.weekOf(LocalDate.now()).coerceAtLeast(0)
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
        // 校区要写进当前学期那一行：课表网格、提醒、静音的作息时间都读它，
        // 只改 DataStore 的话要等下次导入课表才会生效。
        repo.applyCampus(c)
        reminders.reschedule()
        // 两个校区上午整体差 30 分钟，静音时段也得按新作息重算
        silence.reschedule()
    }

    fun setTerm(name: String, startMonday: String, totalWeeks: Int) = safeLaunch {
        // 注意：这里必须落到 Room 的 terms 表上。周次换算是从那一行读 startDate 的，
        // 只写 DataStore 的话「第 1 周周一」改了也不会生效。
        repo.applyTermSettings(
            name = name,
            startMonday = startMonday,
            totalWeeks = totalWeeks,
            campus = settings.value.campus,
        )
        // 「第 1 周周一」决定 weekOf() 的结果：改完这一项，
        // 哪天有课整个变了，提醒和静音都得重排。
        reminders.reschedule()
        silence.reschedule()
        _toast.value = "学期已更新"
    }

    fun setRemind(enabled: Boolean, minutesBefore: Int) = safeLaunch {
        repo.settings.setRemind(enabled, minutesBefore)
        val funny = repo.settingsFlow.first().remindSoundEnabled
        // 渠道要跟着音效设置准备好 —— 音效绑在渠道上且创建后不可改，
        // 见 ReminderReceiver.ensureChannel 的 KDoc。走 scheduler 是因为
        // 它持有 Context，而 ViewModel 不该为了这点事再拿一份。
        runCatching { reminders.prepareChannel(funny) }
        reminders.reschedule()
        _toast.value = if (enabled) "已开启上课提醒" else "已关闭上课提醒"
    }

    /**
     * 切换提醒音效：奶龙捧腹大笑 / 系统默认。
     *
     * 写完设置**必须立刻重建渠道**。Android 8.0+ 起通知音效是渠道的属性，
     * 而且渠道创建后音效就锁死了 —— 只改设置不重建渠道的话，
     * 用户下次听到的还是旧声音，会以为开关坏了。
     */
    fun setRemindSound(enabled: Boolean) = safeLaunch {
        repo.settings.setRemindSound(enabled)
        runCatching { reminders.prepareChannel(enabled) }
        _toast.value = if (enabled) "提醒音效：奶龙捧腹大笑" else "提醒音效：系统默认"
    }

    fun setUrls(kb: String, exam: String, grade: String) = safeLaunch {
        repo.settings.setUrls(kb, exam, grade)
        _toast.value = "抓取地址已保存"
    }

    /**
     * 保存 AI 服务配置（设置 → AI 搜索）。
     *
     * 三个字段一次写：它们只有凑成一次可用请求才有意义，
     * 分开写会留下「URL 是新的、模型名还是旧的」这种半配置状态。
     */
    fun setAi(baseUrl: String, apiKey: String, model: String) = safeLaunch {
        // 走 repo.settings（和考研仓库共用同一个 SettingsStore 实例）：
        // 设置页只需要把三项原样落盘，没必要为写个配置再拉一层仓库方法。
        repo.settings.setAi(baseUrl, apiKey, model)
        // 提示按**落盘后回读到的值**判断，而不是按入参：setAi 会 trim、
        // 模型名留空还会回落到默认值。按入参判断会把「只敲了空格的密钥」
        // 报成「已保存」，用户到 AI 页才发现用不了。
        val ready = runCatching { kaoyan.settingsFlow.first().aiConfig.isReady }
            .getOrDefault(false)
        _toast.value = if (ready) {
            "AI 配置已保存"
        } else {
            "AI 配置已保存，但 base URL、密钥或模型名还缺一项，AI 搜索暂不可用"
        }
    }

    /** 保存考研目录的远端刷新地址。留空 = 只用内置目录。 */
    fun setKaoyanCatalogUrl(url: String) = safeLaunch {
        repo.settings.setKaoyanCatalogUrl(url)
        _toast.value = if (url.isBlank()) {
            "已清空目录地址，改用内置目录"
        } else {
            "考研目录地址已保存"
        }
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

    /**
     * 性能模式。
     *
     * 打开 = 底栏玻璃强制降级为纯色：不把整屏录进 GraphicsLayer、不实时模糊。
     * 这是这个 App 唯一"每帧都在烧 GPU"的地方，低内存设备的默认值就是开。
     */
    fun setPerformanceMode(enabled: Boolean) = safeLaunch {
        repo.settings.setPerformanceMode(enabled)
        _toast.value = if (enabled) {
            "性能模式已开：底栏改用纯色，不再实时模糊"
        } else {
            "性能模式已关：恢复底栏玻璃效果"
        }
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
