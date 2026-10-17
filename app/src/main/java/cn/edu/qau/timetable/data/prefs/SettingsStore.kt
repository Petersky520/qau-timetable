package cn.edu.qau.timetable.data.prefs

import android.app.ActivityManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.DayOverride
import cn.edu.qau.timetable.core.DayOverrides
import cn.edu.qau.timetable.core.GlassEffect
import cn.edu.qau.timetable.core.SilenceMode
import cn.edu.qau.timetable.core.UiStyle
import cn.edu.qau.timetable.core.kaoyan.AiConfig
import cn.edu.qau.timetable.data.qz.QzEndpoints
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "qau_settings")

data class AppSettings(
    val campus: Campus = Campus.CHENGYANG,
    val termName: String = "",
    /** 第 1 周周一，ISO yyyy-MM-dd */
    val startMonday: String = "",
    val totalWeeks: Int = 20,
    val remindEnabled: Boolean = false,
    val remindMinutesBefore: Int = 15,
    /**
     * 上课自动静音。
     * 默认**关闭** —— 它会改动系统铃声，必须由用户主动开启，
     * 而且还需要用户去系统设置里授予「通知策略访问权限」。
     */
    val silenceEnabled: Boolean = false,
    /** 静音期间把铃声调成完全静音还是仅震动。 */
    val silenceMode: SilenceMode = SilenceMode.SILENT,
    /**
     * 调休安排：哪些日期按哪一天的课表上课、哪些日期放假。
     *
     * 存在 DataStore 而不是数据库表里 —— 一学期就这么几条，
     * 而给 Room 加表要升 schema 版本，本 App 用的是
     * `fallbackToDestructiveMigration()`，升版本会把用户的课表成绩清掉。
     */
    val dayOverrides: List<DayOverride> = emptyList(),
    /** Material You 动态取色（跟随手机壁纸）。只对 Material 3 风格生效。 */
    val dynamicColor: Boolean = true,
    /** 界面风格：Material 3（默认）或 MIUI X。 */
    val uiStyle: UiStyle = UiStyle.MATERIAL3,
    /**
     * 底栏的玻璃效果：无 / 高斯模糊。
     *
     * 对原版底栏和悬浮底栏都生效 —— 选「无」时底栏会回到完全不透明、
     * 由 Scaffold 正常占位的那副样子。
     */
    val glassEffect: GlassEffect = GlassEffect.GAUSSIAN,
    /**
     * 悬浮底栏。
     *
     * 打开：底栏是一枚浮在内容之上的玻璃胶囊，内容从底下穿过去、被模糊。
     * 关闭：回到原来那种贴底、占位、不透明的 NavigationBar。
     *
     * 留这个开关是因为悬浮底栏依赖 GraphicsLayer 录屏 + 模糊，
     * 属于"能用但没必要人人都用"的东西 —— 而且有人就是更习惯原版那种踏实感。
     */
    val floatingBottomBar: Boolean = true,
    /**
     * 性能模式。
     *
     * 开启后底栏玻璃效果**一律降级为纯色**：不再把整屏录进 GraphicsLayer、
     * 不再做实时模糊。这是这个 App 里唯一"每帧都在烧 GPU"的地方，
     * 低端机上关掉它换来的流畅度远比那层磨砂玻璃值钱。
     *
     * 默认值**跟随设备**（[SettingsStore] 用 `ActivityManager.isLowRamDevice` 判定），
     * 这里的 false 只是 JVM 单测里 `AppSettings()` 的兜底。
     */
    val performanceMode: Boolean = false,
    val kbUrl: String = QzEndpoints.TIMETABLE,
    val examUrl: String = QzEndpoints.EXAMS,
    val gradeUrl: String = QzEndpoints.GRADES,
    // 原先还有 classroomUrl（空闲教室页）。该功能已下线，设置项一并移除；
    // DataStore 里残留的 url_classroom 键不再读取，也不影响其它设置。

    // ------------------------------------------------------------ 考研模块
    /** OpenAI 兼容的 base URL，如 https://api.deepseek.com/v1；空 = 未配置。 */
    val aiBaseUrl: String = "",
    /**
     * AI 服务的密钥。
     *
     * 只落在本机 DataStore，不进仓库、不上传；UI 侧用密码样式输入框回显。
     * 任何日志都不许打它 —— 所以 `toString()` 在下面被单独覆盖掉了。
     */
    val aiApiKey: String = "",
    val aiModel: String = "deepseek-chat",
    /** 考研目录的远端刷新地址；空 = 只用内置种子目录。 */
    val kaoyanCatalogUrl: String = "",
    /** 收藏的院校 id，逗号分隔。 */
    val kaoyanFavorites: String = "",
) {
    /** 组装成 AI 客户端直接吃的配置。 */
    val aiConfig: AiConfig get() = AiConfig(aiBaseUrl, aiApiKey, aiModel)

    /**
     * 真正生效的底栏效果。
     *
     * 性能模式是**覆盖式**的：它不改用户的 [glassEffect] 选择（设置页那颗 chip 仍然
     * 显示用户选过的东西），只在渲染路径上把它压平成「无」。
     * 这样关掉性能模式时，用户原来的偏好原封不动地回来 ——
     * 如果写回成 NONE，等于替用户改了设置，再打开就丢了他选过的效果。
     */
    val effectiveGlassEffect: GlassEffect
        get() = if (performanceMode) GlassEffect.NONE else glassEffect

    val favoriteIds: List<String> get() = kaoyanFavorites.split(',').filter { it.isNotBlank() }

    /**
     * 覆盖默认的 `toString()`。
     *
     * data class 自动生成的 `toString()` 会把 apiKey 原样拼进去，
     * 而 `AppSettings` 免不了被丢进日志或错误上报 —— 手滑一次密钥就落地了。
     * 这里把它打码，剩下的字段保持可读，排查问题不受影响。
     */
    override fun toString(): String =
        "AppSettings(campus=$campus, termName='$termName', aiBaseUrl='$aiBaseUrl', " +
            "aiApiKey=${if (aiApiKey.isBlank()) "''" else "'***'"}, aiModel='$aiModel')"
}

class SettingsStore(private val context: Context) {

    /**
     * 性能模式的**出厂默认值**：低内存设备默认开。
     *
     * `ActivityManager.isLowRamDevice` 是系统对「这台机器跑不动花哨东西」的官方判断
     * （厂商在 build.prop 里标 `ro.config.low_ram=true`，或 RAM 小于阈值）。
     * 拿它当默认值，比按 SDK 版本 / 机型白名单猜要靠谱得多：
     * 厂商自己最清楚这台机器几斤几两。
     *
     * 只算一次并缓存 —— 这个值在进程生命周期内不会变，
     * 而它在每次 `dataStore.data` 发射时都要用（设置页每改一项都会重发）。
     */
    private val defaultPerformanceMode: Boolean by lazy {
        runCatching {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .isLowRamDevice
        }.getOrDefault(false)
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            campus = Campus.fromName(p[KEY_CAMPUS]),
            termName = p[KEY_TERM] ?: "",
            startMonday = p[KEY_START] ?: "",
            totalWeeks = p[KEY_WEEKS] ?: 20,
            remindEnabled = p[KEY_REMIND] ?: false,
            remindMinutesBefore = p[KEY_REMIND_MIN] ?: 15,
            silenceEnabled = p[KEY_SILENCE] ?: false,
            silenceMode = SilenceMode.fromName(p[KEY_SILENCE_MODE]),
            dayOverrides = DayOverrides.decode(p[KEY_DAY_OVERRIDES].orEmpty()),
            dynamicColor = p[KEY_DYNAMIC_COLOR] ?: true,
            uiStyle = UiStyle.fromName(p[KEY_UI_STYLE]),
            glassEffect = GlassEffect.fromName(p[KEY_GLASS_EFFECT]),
            floatingBottomBar = p[KEY_FLOATING_BAR] ?: true,
            // 没存过 = 用户没表过态 → 按设备能力给默认值（低内存机默认开）。
            // 一旦用户手动拨过开关，就以他拨的为准，不再跟随设备。
            performanceMode = p[KEY_PERFORMANCE_MODE] ?: defaultPerformanceMode,
            kbUrl = p[KEY_KB_URL] ?: QzEndpoints.TIMETABLE,
            examUrl = p[KEY_EXAM_URL] ?: QzEndpoints.EXAMS,
            gradeUrl = p[KEY_GRADE_URL] ?: QzEndpoints.GRADES,
            aiBaseUrl = p[KEY_AI_BASE_URL] ?: "",
            aiApiKey = p[KEY_AI_API_KEY] ?: "",
            aiModel = p[KEY_AI_MODEL] ?: "deepseek-chat",
            kaoyanCatalogUrl = p[KEY_KAOYAN_CATALOG_URL] ?: "",
            kaoyanFavorites = p[KEY_KAOYAN_FAVORITES] ?: "",
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun setCampus(c: Campus) = edit { it[KEY_CAMPUS] = c.name }
    suspend fun setTerm(name: String, startMonday: String, totalWeeks: Int) = edit {
        it[KEY_TERM] = name
        it[KEY_START] = startMonday
        it[KEY_WEEKS] = totalWeeks
    }
    suspend fun setRemind(enabled: Boolean, minutesBefore: Int) = edit {
        it[KEY_REMIND] = enabled
        it[KEY_REMIND_MIN] = minutesBefore
    }

    suspend fun setSilence(enabled: Boolean, mode: SilenceMode) = edit {
        it[KEY_SILENCE] = enabled
        it[KEY_SILENCE_MODE] = mode.name
    }

    suspend fun setDynamicColor(enabled: Boolean) = edit {
        it[KEY_DYNAMIC_COLOR] = enabled
    }

    suspend fun setUiStyle(style: UiStyle) = edit {
        it[KEY_UI_STYLE] = style.name
    }

    suspend fun setGlassEffect(effect: GlassEffect) = edit {
        it[KEY_GLASS_EFFECT] = effect.name
    }

    suspend fun setFloatingBottomBar(enabled: Boolean) = edit {
        it[KEY_FLOATING_BAR] = enabled
    }

    /**
     * 性能模式开关。
     *
     * 写进去的永远是**显式**的值：从这一刻起它就不再跟随设备默认值了。
     * 想「恢复自动」只能清数据 —— 这里不做三态，因为一个只有两档的开关
     * 加第三档「自动」会让设置页多一行看不懂的东西，而低内存设备的默认值
     * 本来就已经是对的。
     */
    suspend fun setPerformanceMode(enabled: Boolean) = edit {
        it[KEY_PERFORMANCE_MODE] = enabled
    }

    /** 整份替换调休安排（增删都在 UI 侧算好再写回）。 */
    suspend fun setDayOverrides(list: List<DayOverride>) = edit {
        it[KEY_DAY_OVERRIDES] = DayOverrides.encode(list)
    }
    suspend fun setUrls(kb: String, exam: String, grade: String) = edit {
        it[KEY_KB_URL] = kb
        it[KEY_EXAM_URL] = exam
        it[KEY_GRADE_URL] = grade
    }

    // ------------------------------------------------------------ 考研模块

    /**
     * 保存 AI 服务配置。
     *
     * 三个字段一次性写：它们只在「能构成一次可用请求」时才有意义，
     * 分开写会出现 base URL 已经是新的、模型名还是旧的这种半配置状态。
     */
    suspend fun setAi(baseUrl: String, apiKey: String, model: String) = edit {
        it[KEY_AI_BASE_URL] = baseUrl.trim()
        it[KEY_AI_API_KEY] = apiKey.trim()
        it[KEY_AI_MODEL] = model.trim().ifBlank { "deepseek-chat" }
    }

    suspend fun setKaoyanCatalogUrl(url: String) = edit {
        it[KEY_KAOYAN_CATALOG_URL] = url.trim()
    }

    /** 整份替换收藏列表（增删都在调用侧算好再写回，和调休安排一致）。 */
    suspend fun setKaoyanFavorites(ids: List<String>) = edit {
        it[KEY_KAOYAN_FAVORITES] = ids.filter { it.isNotBlank() }.distinct().joinToString(",")
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val KEY_CAMPUS = stringPreferencesKey("campus")
        val KEY_TERM = stringPreferencesKey("term_name")
        val KEY_START = stringPreferencesKey("term_start")
        val KEY_WEEKS = intPreferencesKey("term_weeks")
        val KEY_REMIND = booleanPreferencesKey("remind_enabled")
        val KEY_REMIND_MIN = intPreferencesKey("remind_minutes")
        val KEY_SILENCE = booleanPreferencesKey("silence_enabled")
        val KEY_SILENCE_MODE = stringPreferencesKey("silence_mode")
        val KEY_DAY_OVERRIDES = stringPreferencesKey("day_overrides")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_UI_STYLE = stringPreferencesKey("ui_style")
        val KEY_GLASS_EFFECT = stringPreferencesKey("glass_effect")
        val KEY_FLOATING_BAR = booleanPreferencesKey("floating_bottom_bar")
        val KEY_PERFORMANCE_MODE = booleanPreferencesKey("performance_mode")
        val KEY_KB_URL = stringPreferencesKey("url_kb")
        val KEY_EXAM_URL = stringPreferencesKey("url_exam")
        val KEY_GRADE_URL = stringPreferencesKey("url_grade")
        // KEY_CLASSROOM_URL = stringPreferencesKey("url_classroom") 已随功能下线删除
        val KEY_AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val KEY_AI_API_KEY = stringPreferencesKey("ai_api_key")
        val KEY_AI_MODEL = stringPreferencesKey("ai_model")
        val KEY_KAOYAN_CATALOG_URL = stringPreferencesKey("kaoyan_catalog_url")
        val KEY_KAOYAN_FAVORITES = stringPreferencesKey("kaoyan_favorites")
    }
}
