package cn.edu.qau.timetable.ui.kaoyan

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.qau.timetable.BuildConfig
import cn.edu.qau.timetable.core.kaoyan.AiResult
import cn.edu.qau.timetable.core.kaoyan.Ambition
import cn.edu.qau.timetable.core.kaoyan.KaoyanProfile
import cn.edu.qau.timetable.core.kaoyan.Major
import cn.edu.qau.timetable.core.kaoyan.RecommendEngine
import cn.edu.qau.timetable.core.kaoyan.Recommendation
import cn.edu.qau.timetable.core.kaoyan.Region
import cn.edu.qau.timetable.core.kaoyan.SchoolItem
import cn.edu.qau.timetable.core.kaoyan.SchoolTier
import cn.edu.qau.timetable.data.prefs.AppSettings
import cn.edu.qau.timetable.data.repo.KaoyanRepository
import cn.edu.qau.timetable.data.repo.RefreshResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * AI 问答的状态机。
 *
 * 只有三态：没问 / 正在问 / 有答案。**失败不在这里**，它走
 * [KaoyanViewModel.aiError] —— 一旦让失败也占住「有答案」的位置，
 * 用户会看到上一条回答被新错误顶掉，反而不知道哪段才是 AI 说的。
 */
sealed interface AiState {
    data object Idle : AiState
    data object Loading : AiState
    data class Ok(val text: String) : AiState
}

/**
 * 性能计时日志的统一出口。
 *
 * 只在 debug 构建里真正调用 `Log`：
 * - `Log.d` 在 release 里虽然通常会被 R8 去掉，但字符串拼接发生在**调用前**，
 *   是不会被去掉的那部分开销；
 * - 更实际的原因：`Log` 是 android.util 的东西，`KaoyanViewModel` 一旦静态引用它，
 *   纯 JVM 单测里就必须有 android.jar 打桩（`unitTests.returnDefaultValues`）。
 *   用 `BuildConfig.DEBUG` 常数条件包住之后，debug 变体照常打、release 里整段消失。
 *
 * 采集方式（可复现）：
 *   adb logcat -s KaoyanPerf
 * 进入考研页、改动一次意向（或刷新目录）即可看到每次重算的条目数与耗时。
 */
private fun perfLog(message: String) {
    if (BuildConfig.DEBUG) Log.d("KaoyanPerf", message)
}

/**
 * 考研模块的 ViewModel。
 *
 * ## 为什么和 MainViewModel 分开
 * 「画像变化 → 重算推荐」是一条独立的 combine 链，和课表/成绩那套完全无关。
 * 塞进 MainViewModel 只会让那个类越来越像个杂物间；而独立出来之后，
 * 四个子页（选校 / 论文 / 资料 / AI）共享同一份实例，
 * 在论文页挑好关键词再切回选校页，用户的意向也不会被重置。
 *
 * ## 为什么只依赖仓库
 * UI 层不直接碰 Room 和 DataStore：换存储实现时页面一行都不用改，
 * 也让这个类可以在纯 JVM 里被构造（[KaoyanRepository] 之外没有 Android 类型）。
 */
class KaoyanViewModel(
    private val repo: KaoyanRepository,
) : ViewModel() {

    /** 设置：AI 配置、目录地址、收藏都在这一份里。 */
    val settings: StateFlow<AppSettings> = repo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** 本地院校目录（种子 + 远端刷新后的结果）。 */
    val schools: StateFlow<List<SchoolItem>> = repo.observeSchools()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 收藏的院校 id。 */
    val favorites: StateFlow<Set<String>> = settings
        .map { it.favoriteIds.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _profile = MutableStateFlow(KaoyanProfile())

    /** 报考意向画像。UI 只做 copy()，不在页面里散落一堆 mutableState。 */
    val profile: StateFlow<KaoyanProfile> = _profile.asStateFlow()

    /**
     * 推荐结果。
     *
     * 用 `WhileSubscribed(5000)` 而不是 `Eagerly`：这页的计算量随目录条数线性增长，
     * 用户切到论文页时没必要继续重算；5 秒的宽限是为了让「切走再切回」不重算。
     */
    val recommendations: StateFlow<List<Recommendation>> =
        combine(schools, _profile) { list, p ->
            // 计时证据（见 docs/v33-spec.md §2.2-H）：只在 debug 构建里打。
            // 用 SystemClock 而不是 System.currentTimeMillis —— 前者是单调时钟，
            // 不会被系统校时 / 时区调整拉回去，测时长必须用它。
            val t0 = SystemClock.elapsedRealtime()
            val recs = RecommendEngine.recommend(p, list)
            val ms = SystemClock.elapsedRealtime() - t0
            perfLog("recommend ${list.size} -> ${recs.size} in ${ms}ms")
            recs
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 推荐结果的**展示投影**。
     *
     * 为什么在这里映射一次，而不是在 composable 里：
     * `Recommendation` / `SchoolScore` / `SchoolItem` 住在 `core/kaoyan/`（纯 Kotlin，
     * 不许 import Compose），所以标不了 `@Immutable`，Compose 会把它们当成不稳定类型，
     * 父级重组就带着视口里所有卡片一起重组。这里一次性投影成 [KaoyanCardUi]（`@Immutable`），
     * 列表项进组合之后父级重组可以被直接跳过。
     *
     * 映射发生在 Flow 里（每次 recommend 结果变化跑一次 O(N)），
     * 不发生在每一帧的 composition 里 —— 后者等于把优化又还回去了。
     */
    val recommendCards: StateFlow<List<KaoyanCardUi>> =
        recommendations
            .map { recs ->
                val t0 = SystemClock.elapsedRealtime()
                val cards = recs.map { it.toCardUi() }
                perfLog("mapToUi ${recs.size} -> ${cards.size} in ${SystemClock.elapsedRealtime() - t0}ms")
                cards
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _aiAnswer = MutableStateFlow<AiState>(AiState.Idle)
    val aiAnswer: StateFlow<AiState> = _aiAnswer.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)

    /** 最近一次 AI 调用的失败原因；成功时清空。 */
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _busy = MutableStateFlow(false)

    /** 灌种子 / 刷新目录期间为 true，页面据此显示进度条并禁用刷新按钮。 */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init {
        // 首次打开把 assets 里的样例目录灌进 Room。失败不打断页面：
        // 库里已有的数据照常展示，用户在设置页填了刷新地址还能再拉一次。
        viewModelScope.launch {
            _busy.value = true
            try {
                repo.ensureSeeded()
            } catch (t: Throwable) {
                _toast.value = "院校目录初始化失败：${t.javaClass.simpleName}"
            } finally {
                _busy.value = false
            }
        }
    }

    // ------------------------------------------------------------ 意向

    fun setMajor(m: Major) { _profile.value = _profile.value.copy(major = m) }

    fun setRegion(r: Region) { _profile.value = _profile.value.copy(region = r) }

    fun setAmbition(a: Ambition) { _profile.value = _profile.value.copy(ambition = a) }

    fun setUndergraduateTier(t: SchoolTier) {
        _profile.value = _profile.value.copy(undergraduateTier = t)
    }

    fun setCrossMajor(b: Boolean) { _profile.value = _profile.value.copy(crossMajor = b) }

    fun setAcceptMath(b: Boolean) { _profile.value = _profile.value.copy(acceptMath = b) }

    fun setMinQuota(n: Int) { _profile.value = _profile.value.copy(minQuota = n.coerceAtLeast(0)) }

    // ------------------------------------------------------------ 目录

    /**
     * 从远端刷新目录。
     *
     * 地址为空时不去发一个必然失败的请求，而是直接把用户指到设置页 ——
     * 「刷新失败：刷新地址为空」这种文案只会让人以为网络坏了。
     */
    fun refreshCatalog(url: String) {
        val target = url.trim()
        if (target.isEmpty()) {
            _toast.value = "还没填目录刷新地址。去「设置 → AI 搜索」里填一个，或继续用内置目录。"
            return
        }
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                when (val result = repo.refreshFromRemote(target)) {
                    is RefreshResult.Ok -> _toast.value = "目录已更新：${result.count} 条"
                    is RefreshResult.Failed -> _toast.value = result.message
                }
            } catch (t: Throwable) {
                _toast.value = "刷新失败：${t.javaClass.simpleName}"
            } finally {
                _busy.value = false
            }
        }
    }

    // ------------------------------------------------------------ AI

    /** 问一次 AI。失败原因单独落到 [aiError]，不冒充「回答」。 */
    fun askAi(question: String) {
        val q = question.trim()
        if (q.isEmpty()) {
            _toast.value = "先写一句想问的问题"
            return
        }
        if (_aiAnswer.value is AiState.Loading) return
        viewModelScope.launch {
            _aiError.value = null
            _aiAnswer.value = AiState.Loading
            val result = try {
                repo.askAi(q)
            } catch (t: Throwable) {
                AiResult.Failed("调用失败：${t.javaClass.simpleName}")
            }
            when (result) {
                is AiResult.Ok -> _aiAnswer.value = AiState.Ok(result.text.trim())
                is AiResult.Failed -> {
                    _aiAnswer.value = AiState.Idle
                    _aiError.value = result.message
                    _toast.value = result.message
                }
            }
        }
    }

    // ------------------------------------------------------------ 收藏

    fun toggleFavorite(id: String) {
        if (id.isBlank()) return
        viewModelScope.launch {
            // 仓库内部已经把异常吃掉了；这里再兜一层，收藏失败不该让页面崩。
            runCatching { repo.toggleFavorite(id) }
        }
    }

    /**
     * 页面级提示。
     *
     * 给「链接打不开」「先输关键词」这类**纯 UI 事件**用：它们不是数据层的结果，
     * 塞进 [refreshCatalog] / [askAi] 的返回里只会让那些方法变得不纯。
     * 提示最终由 `KaoyanScreen` 转发到全局 Snackbar。
     */
    fun notify(message: String) { _toast.value = message }

    fun clearToast() { _toast.value = null }
}
