package cn.edu.qau.timetable.data.prefs

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
import cn.edu.qau.timetable.core.SilenceMode
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
     * 上课提醒是否用「奶龙捧腹大笑」音效，而不是系统默认提示音。
     *
     * ## 为什么默认关闭
     *
     * 这是一个**明显吵**的音效。默认开会让所有用户都被它吓一跳，
     * 而且上课时通知突然大笑在教室里非常尴尬。
     * 想要的人自己去打开 —— 默认值是产品态度，不是技术限制。
     *
     * ## 为什么改了它要重建通知渠道
     *
     * Android 8.0+ 起，通知音效是**渠道（NotificationChannel）的属性**，
     * 而且渠道一旦创建，音效/震动/重要性就**不能再修改** ——
     * 系统会直接忽略后续的修改请求（这是 Android 的硬规则，不是 App 偷懒）。
     *
     * 所以切换这个开关时，[cn.edu.qau.timetable.notify.ReminderReceiver.ensureChannel]
     * 会用一个不同的渠道 id 重建渠道。旧渠道删掉：
     * 不删的话设置里会堆积一串「上课提醒」条目，用户分不清哪个是活的。
     */
    val remindSoundEnabled: Boolean = false,
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
    val dynamicColor: Boolean = true,
    val timetableWeekGrid: Boolean = false,
    // 保留旧版已保存的教务地址。同步界面会验证学校域名，并可回退到内置入口。
    val kbUrl: String = QzEndpoints.TIMETABLE,
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

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            campus = Campus.fromName(p[KEY_CAMPUS]),
            termName = p[KEY_TERM] ?: "",
            startMonday = p[KEY_START] ?: "",
            totalWeeks = p[KEY_WEEKS] ?: 20,
            remindEnabled = p[KEY_REMIND] ?: false,
            remindMinutesBefore = p[KEY_REMIND_MIN] ?: 15,
            remindSoundEnabled = p[KEY_REMIND_SOUND] ?: false,
            silenceEnabled = p[KEY_SILENCE] ?: false,
            silenceMode = SilenceMode.fromName(p[KEY_SILENCE_MODE]),
            dayOverrides = DayOverrides.decode(p[KEY_DAY_OVERRIDES].orEmpty()),
            dynamicColor = p[KEY_DYNAMIC_COLOR] ?: true,
            timetableWeekGrid = p[KEY_TIMETABLE_WEEK_GRID] ?: false,
            kbUrl = p[KEY_KB_URL] ?: QzEndpoints.TIMETABLE,
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

    /**
     * 切换提醒音效。
     *
     * 调用方（MainViewModel）在写完之后要**重建通知渠道** ——
     * Android 8.0+ 的音效绑在渠道上且创建后不可改，改设置不重建渠道等于没生效。
     */
    suspend fun setRemindSound(enabled: Boolean) = edit {
        it[KEY_REMIND_SOUND] = enabled
    }

    suspend fun setSilence(enabled: Boolean, mode: SilenceMode) = edit {
        it[KEY_SILENCE] = enabled
        it[KEY_SILENCE_MODE] = mode.name
    }

    suspend fun setDynamicColor(enabled: Boolean) = edit {
        it[KEY_DYNAMIC_COLOR] = enabled
    }

    suspend fun setTimetableWeekGrid(enabled: Boolean) = edit {
        it[KEY_TIMETABLE_WEEK_GRID] = enabled
    }

    /** 整份替换调休安排（增删都在 UI 侧算好再写回）。 */
    suspend fun setDayOverrides(list: List<DayOverride>) = edit {
        it[KEY_DAY_OVERRIDES] = DayOverrides.encode(list)
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
        val KEY_REMIND_SOUND = booleanPreferencesKey("remind_sound_enabled")
        val KEY_SILENCE = booleanPreferencesKey("silence_enabled")
        val KEY_SILENCE_MODE = stringPreferencesKey("silence_mode")
        val KEY_DAY_OVERRIDES = stringPreferencesKey("day_overrides")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_TIMETABLE_WEEK_GRID = booleanPreferencesKey("timetable_week_grid")
        val KEY_KB_URL = stringPreferencesKey("url_kb")
        val KEY_GRADE_URL = stringPreferencesKey("url_grade")
        // KEY_CLASSROOM_URL = stringPreferencesKey("url_classroom") 已随功能下线删除
        val KEY_AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val KEY_AI_API_KEY = stringPreferencesKey("ai_api_key")
        val KEY_AI_MODEL = stringPreferencesKey("ai_model")
        val KEY_KAOYAN_CATALOG_URL = stringPreferencesKey("kaoyan_catalog_url")
        val KEY_KAOYAN_FAVORITES = stringPreferencesKey("kaoyan_favorites")
    }
}
