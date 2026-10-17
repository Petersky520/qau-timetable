package cn.edu.qau.timetable.data.repo

import android.content.Context
import cn.edu.qau.timetable.core.kaoyan.AiConfig
import cn.edu.qau.timetable.core.kaoyan.AiMessage
import cn.edu.qau.timetable.core.kaoyan.AiResult
import cn.edu.qau.timetable.core.kaoyan.SchoolItem
import cn.edu.qau.timetable.data.db.AppDatabase
import cn.edu.qau.timetable.data.kaoyan.FetchResult
import cn.edu.qau.timetable.data.kaoyan.KaoyanAiClient
import cn.edu.qau.timetable.data.kaoyan.KaoyanFetcher
import cn.edu.qau.timetable.data.kaoyan.parseCatalogJson
import cn.edu.qau.timetable.data.model.KaoyanSchoolEntity
import cn.edu.qau.timetable.data.prefs.AppSettings
import cn.edu.qau.timetable.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * 考研模块的数据仓库。
 *
 * 和 [TimetableRepository] 一样，**所有 suspend 方法都不把异常抛给调用方**：
 * 调用点在 ViewModel 的协程里，异常一旦逃出去就是闪退。
 * 需要区分成功/失败的地方用 sealed 结果（[RefreshResult] / [AiResult]），
 * 不需要区分的（灌种子、收藏）返回条数或布尔值，内部把异常吃成"没变化"。
 */
class KaoyanRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
) {

    private val fetcher = KaoyanFetcher()
    private val ai = KaoyanAiClient()

    val settingsFlow: Flow<AppSettings> = settings.flow

    // ------------------------------------------------------------ 院校目录

    /**
     * 首次打开时把 assets 里的种子灌进数据库；已有数据则跳过。
     *
     * 返回当前库里的条目数（不是"这次插入了几条"）—— 调用方只关心
     * "打开之后有没有数据可看"，而这个数在两种路径下含义一致。
     */
    suspend fun ensureSeeded(): Int = withContext(Dispatchers.IO) {
        try {
            val existing = db.kaoyanSchoolDao().count()
            if (existing > 0) return@withContext existing

            val raw = context.assets.open(SEED_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val items = parseCatalogJson(raw)
            if (items.isEmpty()) return@withContext 0

            db.kaoyanSchoolDao().insertAll(items.map { KaoyanSchoolEntity.fromDomain(it) })
            db.kaoyanSchoolDao().count()
        } catch (t: Throwable) {
            // 种子读不出来不该挡住整个模块：库里有什么就先展示什么，
            // 用户在设置页填了刷新地址还能再拉一次。
            0
        }
    }

    fun observeSchools(): Flow<List<SchoolItem>> =
        db.kaoyanSchoolDao().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun schoolsNow(): List<SchoolItem> =
        db.kaoyanSchoolDao().allNow().map { it.toDomain() }

    /**
     * 从远端刷新；成功则整体替换本地目录。
     *
     * 失败时**不动**本地数据：拉回来的是半截或空目录就把用户的目录清掉，
     * 比不刷新糟糕得多。
     */
    suspend fun refreshFromRemote(url: String): RefreshResult {
        return when (val result = fetcher.fetchCatalog(url)) {
            is FetchResult.Failed -> RefreshResult.Failed(result.message)
            is FetchResult.Ok -> try {
                withContext(Dispatchers.IO) {
                    db.kaoyanSchoolDao().replaceAll(result.schools.map { KaoyanSchoolEntity.fromDomain(it) })
                }
                RefreshResult.Ok(result.schools.size)
            } catch (t: Throwable) {
                RefreshResult.Failed("写入失败：${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    // ------------------------------------------------------------ AI 搜索

    /**
     * 问一次 AI。
     *
     * 这里只负责组装 prompt 和取配置；超时、HTTP 状态、解析全在
     * [KaoyanAiClient] 里被翻译成中文文案，仓库层不再二次包装 ——
     * 包装一层就会变成「网络错误：密钥无效或没有权限（HTTP 401）」这种叠词。
     */
    suspend fun askAi(question: String): AiResult {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return AiResult.Failed("先写一句想问的问题")

        val config: AiConfig = try {
            settings.current().aiConfig
        } catch (t: Throwable) {
            AiConfig()
        }
        val messages = listOf(
            AiMessage(role = "system", content = SYSTEM_PROMPT),
            AiMessage(role = "user", content = trimmed),
        )
        return ai.ask(config, messages)
    }

    // ------------------------------------------------------------ 收藏

    suspend fun setFavorite(id: String, on: Boolean) {
        if (id.isBlank()) return
        try {
            val current = settings.current().favoriteIds
            val next = if (on) {
                if (id in current) current else current + id
            } else {
                current.filterNot { it == id }
            }
            if (next != current) settings.setKaoyanFavorites(next)
        } catch (t: Throwable) {
            // 收藏写失败只影响那颗星，不该把界面打断。
        }
    }

    /** 返回切换后的状态（true = 已收藏）。 */
    suspend fun toggleFavorite(id: String): Boolean {
        if (id.isBlank()) return false
        return try {
            val current = settings.current().favoriteIds
            val nowFavorite = id !in current
            settings.setKaoyanFavorites(if (nowFavorite) current + id else current - id)
            nowFavorite
        } catch (t: Throwable) {
            false
        }
    }

    companion object {
        private const val SEED_ASSET = "kaoyan/schools.json"

        /**
         * 之所以把「不确定就说不确定」写进 prompt：这个模型被拿来问分数线，
         * 一本正经地编一个不存在的数字，比说"不知道"危害大得多。
         */
        private const val SYSTEM_PROMPT =
            "你是考研规划助手，回答要具体、给数据、给来源，不确定就说不确定。回答控制在 400 字以内。"
    }
}

/** 刷新结果。语义与 [FetchResult] 一致，但不把抓取器的类型泄漏到 UI 层。 */
sealed interface RefreshResult {
    data class Ok(val count: Int) : RefreshResult
    data class Failed(val message: String) : RefreshResult
}
