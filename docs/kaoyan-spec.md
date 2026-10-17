# 考研工具模块 —— 实现契约（唯一规范）

本文件是新增「考研工具」模块的**接口契约**。所有并行开发都以本文件为准，
不得自行改签名。签名不一致由集成验证任务负责裁决。

包名根：`cn.edu.qau.timetable`

---

## 0. 硬性约束

- 语言 Kotlin，UI 用 Jetpack Compose Material 3，沿用现有代码风格：
  中文注释、`/** ... */` KDoc 说明「为什么」而不是「做什么」、不写无意义缩写。
- **纯逻辑层（`core/kaoyan/`）不得 import 任何 `android.*` 或 `androidx.*`** ——
  必须能在 JVM 单测里直接跑。
- 所有面向用户的文案用简体中文。
- 不引入新依赖。可用的只有：现有 androidx compose / room / datastore / junit4，
  以及 JDK 自带的 `java.net.HttpURLConnection`、`org.json`（Android 内置）。
- 新增文件不得改动既有类（`MainViewModel` 除外，见 §6）。
- 每个实现任务结束前必须 `assembleDebug` 通过。

构建命令（本机 JDK 17 不在 PATH，必须显式指定）：

```powershell
cd "D:\Agent工作区\Android开发\qau-timetable"
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat --no-daemon assembleDebug
```

> 注意：本工程路径含中文，AGP 需要 `android.overridePathCheck=true`
> （已在 `~/.gradle/gradle.properties` 里配置好）。`local.properties` 也已生成。

---

## 1. 领域模型 `core/kaoyan/`

### 1.1 `Major.kt`

```kotlin
package cn.edu.qau.timetable.core.kaoyan

/**
 * 可选的考研专业意向。
 *
 * 用枚举而不是自由文本：推荐算法要按专业匹配院校的招生专业，
 * 自由文本会让「计算机」「计算机科学与技术」「计算机技术」变成三个互不相干的键。
 */
enum class Major(
    val label: String,
    /** 教育部学科门类代码，用于和院校目录对齐。 */
    val code: String,
    /** 该专业是否通常要求考数学（一/二/三）。影响难度评估。 */
    val mathRequired: Boolean,
) {
    COMPUTER("计算机科学与技术", "0812", true),
    SOFTWARE("软件工程", "0835", true),
    ELECTRONIC("电子信息", "0854", true),
    AUTOMATION("控制科学与工程", "0811", true),
    MECHANICAL("机械工程", "0802", true),
    CIVIL("土木工程", "0814", true),
    CHEMISTRY("化学工程与技术", "0817", true),
    FOOD("食品科学与工程", "0832", true),
    AGRONOMY("作物学", "0901", false),
    PLANT_PROTECTION("植物保护", "0904", false),
    ANIMAL_SCIENCE("畜牧学", "0905", false),
    VETERINARY("兽医学", "0906", false),
    HORTICULTURE("园艺学", "0902", false),
    LANDSCAPE("风景园林", "0953", false),
    ECONOMICS("应用经济学", "0202", true),
    MANAGEMENT("农林经济管理", "1203", true),
    FOREIGN_LANG("外国语言文学", "0502", false),
    LAW("法律（非法学）", "0351", false),
    EDUCATION("教育硕士", "0451", false),
    JOURNALISM("新闻与传播", "0552", false),
    ;

    companion object {
        fun fromName(name: String?): Major =
            entries.firstOrNull { it.name == name || it.label == name } ?: COMPUTER
    }
}
```

### 1.2 `SchoolTier.kt`

```kotlin
enum class SchoolTier(val label: String, val weight: Int) {
    C9("C9 / 顶尖", 100),
    PROJECT_985("985", 85),
    PROJECT_211("211", 70),
    DOUBLE_FIRST_CLASS("双一流", 60),
    REGULAR("普通院校", 40),
    ;

    companion object {
        fun fromName(name: String?): SchoolTier =
            entries.firstOrNull { it.name == name || it.label == name } ?: REGULAR
    }
}
```

### 1.3 `Region.kt`

```kotlin
enum class Region(val label: String, val provinces: List<String>) {
    EAST("华东", listOf("山东", "江苏", "浙江", "上海", "安徽", "福建", "江西")),
    NORTH("华北", listOf("北京", "天津", "河北", "山西", "内蒙古")),
    NORTHEAST("东北", listOf("辽宁", "吉林", "黑龙江")),
    CENTRAL("华中", listOf("河南", "湖北", "湖南")),
    SOUTH("华南", listOf("广东", "广西", "海南")),
    SOUTHWEST("西南", listOf("四川", "重庆", "贵州", "云南", "西藏")),
    NORTHWEST("西北", listOf("陕西", "甘肃", "青海", "宁夏", "新疆")),
    ANY("不限", emptyList()),
    ;

    companion object {
        fun fromName(name: String?): Region =
            entries.firstOrNull { it.name == name || it.label == name } ?: ANY
    }
}
```

### 1.4 `Ambition.kt`

```kotlin
/** 报考意愿：冲刺 / 稳妥 / 保底。决定推荐的分数带，而不是「难度」。 */
enum class Ambition(val label: String, val hint: String) {
    REACH("冲刺", "比自己当前水平高一些，值得一冲"),
    MATCH("稳妥", "和当前水平相当，正常发挥就能上"),
    SAFE("保底", "明显低于当前水平，用来兜底"),
    ;

    companion object {
        fun fromName(name: String?): Ambition =
            entries.firstOrNull { it.name == name || it.label == name } ?: MATCH
    }
}
```

### 1.5 `KaoyanProfile.kt`

```kotlin
/**
 * 用户的报考意向画像。
 *
 * 这是推荐引擎的唯一输入，刻意做成不可变 data class + 默认值，
 * 这样 UI 侧只要 `copy()`，不用维护一堆分散的 mutableState。
 */
data class KaoyanProfile(
    val major: Major = Major.COMPUTER,
    val region: Region = Region.ANY,
    val ambition: Ambition = Ambition.MATCH,
    /** 本科院校层次，用来估算「冲」的跨度是否合理。 */
    val undergraduateTier: SchoolTier = SchoolTier.REGULAR,
    /** 是否接受跨专业。跨考时推荐会放宽专业匹配权重。 */
    val crossMajor: Boolean = false,
    /** 是否愿意接受考数学。false 时过滤掉强依赖数学的院校专业。 */
    val acceptMath: Boolean = true,
    /** 期望的最低招生人数（太少的话调剂风险高）。 */
    val minQuota: Int = 0,
)
```

### 1.6 `SchoolItem.kt` —— 放在 `domain/kaoyan/SchoolItem.kt`

（`domain/` 是 UI 与数据层共用的模型层，和现有 `domain/CourseEvent.kt` 同级。）

```kotlin
package cn.edu.qau.timetable.domain.kaoyan

/** 院校招生条目：一个 (院校, 专业) 组合就是一条。 */
data class SchoolItem(
    /** 稳定主键，形如 "10423-0812"，院校代码 + 学科代码。 */
    val id: String,
    val name: String,
    val province: String,
    val city: String,
    val tier: String,
    val majorCode: String,
    val majorName: String,
    /** 近两年复试线，0 表示未收录。 */
    val scoreLast: Int = 0,
    val scorePrev: Int = 0,
    /** 招生人数 / 报考人数，0 表示未收录。 */
    val quota: Int = 0,
    val applicants: Int = 0,
    val mathRequired: Boolean = false,
    /** 标签，逗号分隔，如 "自划线,保护一志愿"。 */
    val tags: String = "",
    val note: String = "",
)
```

### 1.7 `Recommendation.kt` + `RecommendEngine.kt`（`core/kaoyan/`）

```kotlin
data class SchoolScore(
    /** 0..100，越高越匹配。 */
    val score: Int,
    /** 命中的加分理由，逐条给用户看。 */
    val reasons: List<String>,
    /** 被扣分的原因，同样给用户看 —— 只说好话的推荐是骗人的。 */
    val warnings: List<String>,
)

data class Recommendation(
    val school: SchoolItem,       // 注意：core 层不能依赖 domain？
    ...
)
```

> ⚠️ 修正：`core/` 不能依赖 `domain/`（现有 `core/` 是纯 Kotlin 零依赖，
> `GradeSummary.kt` 就特意为此**没有**复用 `domain.GradeItem`）。
> 因此把 `SchoolItem` **放进 `core/kaoyan/`**，`domain/kaoyan/` 不再新建。
> Room 实体自己单独定义、单向映射到 `core.kaoyan.SchoolItem`。

最终位置：`core/kaoyan/SchoolItem.kt`，`package cn.edu.qau.timetable.core.kaoyan`。

```kotlin
data class Recommendation(
    val school: SchoolItem,
    val score: SchoolScore,
    val tier: Ambition,
)

object RecommendEngine {
    /**
     * 按意向画像打分并排序。
     *
     * 分档规则（必须严格实现，UI 会直接展示这三档）：
     *   score >= 75 且 院校层次高于本科层次   -> REACH
     *   score >= 55                           -> MATCH
     *   其余                                   -> SAFE
     * 同时 honors profile.ambition：结果按「该档优先」排序，
     * 但三档都要返回（用户要能横向比较）。
     */
    fun recommend(
        profile: KaoyanProfile,
        schools: List<SchoolItem>,
        limit: Int = 60,
    ): List<Recommendation>

    /** 单条打分，公开出来是为了单测能逐项验证。 */
    fun score(profile: KaoyanProfile, school: SchoolItem): SchoolScore
}
```

打分规则（**必须逐条实现，单测会按此断言**）：

1. 专业匹配：`school.majorCode == profile.major.code` → +30，理由「专业完全对口」；
   否则若 `profile.crossMajor` → +8，理由「允许跨考」；
   否则 **-25**，警告「专业不对口」。
2. 地区匹配：`profile.region == ANY` → +12，「地区不限」；
   `school.province in profile.region.provinces` → +20，「位于意向地区」；
   否则 **-10**，警告「不在意向地区」。
3. 层次差：`delta = school tier weight - profile.undergraduateTier.weight`
   - `delta in 0..15` → +15，「层次相当，跨度合理」
   - `delta in 16..40` → +5，「略有挑战」
   - `delta > 40` → **-15**，警告「跨度较大」
   - `delta < 0` → +10，「相对稳妥」
4. 数学：`school.mathRequired && !profile.acceptMath` → **-20**，警告「该专业需要考数学」。
5. 招生规模：`quota == 0` → 0 分并警告「招生人数未收录」；
   `quota < profile.minQuota` → **-12**，警告「招生人数低于期望」；
   `quota >= 20` → +8，「招生规模较大」。
6. 报录比：`applicants > 0 && quota > 0`：
   ratio = applicants / quota
   `< 3` → +10，「报录比友好」
   `3..8` → 0
   `> 8` → **-10**，警告「报录比偏高」
7. 分数带（结合 ambition）：
   `avg = (scoreLast + scorePrev) / 2`（忽略 0 值；两者都 0 则跳过）
   - REACH：`avg in 330..380` → +8
   - MATCH：`avg in 300..345` → +8
   - SAFE：`avg in 260..310` → +8
   落在带外 → -5，警告「分数线与报考意愿不匹配」。

最终 `score` = 上述累加后 `coerceIn(0, 100)`。
排序：先按 tier（REACH→MATCH→SAFE）顺序，再按 score 降序，再按 name。

---

## 2. 论文 / 资料站点 `core/kaoyan/ResearchSite.kt`

```kotlin
enum class SiteCategory(val label: String) {
    THESIS("论文检索"),
    PATENT("专利 / 标准"),
    STUDY("学习资料"),
    COURSE("公开课"),
    DATA("数据 / 政策"),
    ;

    companion object {
        fun fromName(name: String?): SiteCategory =
            entries.firstOrNull { it.name == name || it.label == name } ?: THESIS
    }
}

/**
 * 一个外部站点。
 *
 * searchTemplate 里用 `{q}` 占位查询词，App 负责替换并 URL 编码 ——
 * 把编码放在调用侧而不是模板里，站点自己的特殊规则（比如知网用 `kw=`）
 * 就能原样保留。
 */
data class ResearchSite(
    val id: String,
    val name: String,
    val category: SiteCategory,
    val homepage: String,
    val searchTemplate: String,
    val description: String,
)
```

`object SiteCatalog { val all: List<ResearchSite> }` 内置目录，**至少 18 条**，覆盖：

- 论文检索：中国知网 CNKI、万方、维普、Google Scholar、Semantic Scholar、
  arXiv、PubMed、DOAJ、CORE、百度学术
- 专利标准：国家知识产权局、SooPAT
- 学习资料：中国大学 MOOC、学堂在线、国家高等教育智慧教育平台、考研帮、研招网、
  Bilibili 搜索、超星尔雅
- 公开课：MIT OCW、Coursera
- 数据政策：中国研究生招生信息网、教育部学位中心

要求：`homepage` 与 `searchTemplate` 用**真实可访问的域名**，
但不需要 App 内实测联网（离线也能展示目录、能拼出正确 URL）。

`fun searchUrl(site: ResearchSite, query: String): String` —— 做 `{q}` 替换 +
`URLEncoder.encode(query, "UTF-8")`（注意 `+` 与 `%20`：用 `%20`）。
放在 `core/kaoyan/SiteCatalog.kt`。

---

## 3. AI 客户端 `core/kaoyan/AiConfig.kt` + `data/kaoyan/KaoyanAiClient.kt`

```kotlin
// core/kaoyan/AiConfig.kt
data class AiConfig(
    /** OpenAI 兼容的 base URL，如 https://api.deepseek.com/v1 */
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "deepseek-chat",
    /** 0 表示用默认 60 秒。 */
    val timeoutSeconds: Int = 60,
) {
    val isReady: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

data class AiMessage(val role: String, val content: String) // role: "system" | "user" | "assistant"

sealed interface AiResult {
    data class Ok(val text: String) : AiResult
    data class Failed(val message: String) : AiResult
}
```

```kotlin
// data/kaoyan/KaoyanAiClient.kt
package cn.edu.qau.timetable.data.kaoyan

class KaoyanAiClient {
    /** 阻塞调用，必须在 Dispatchers.IO 里调。不抛异常，失败走 AiResult.Failed。 */
    suspend fun ask(config: AiConfig, messages: List<AiMessage>): AiResult
}
```

实现要求：
- POST `${baseUrl.trimEnd('/')}/chat/completions`
- Header：`Authorization: Bearer <key>`、`Content-Type: application/json`
- Body：`{"model":..., "messages":[...], "temperature":0.3, "stream":false}`
- 解析 `choices[0].message.content`
- 超时：connect 15s / read 按配置
- 错误映射（必须是这些中文文案）：
  - `isReady == false` → `Failed("还没有配置 AI 服务。到「设置 → AI 搜索」里填 base URL、密钥和模型名。")`
  - 401/403 → `Failed("密钥无效或没有权限（HTTP xxx）")`
  - 429 → `Failed("请求太频繁或额度用完了（HTTP 429）")`
  - 其它非 2xx → `Failed("服务返回 HTTP xxx：<响应前 200 字>")`
  - `SocketTimeoutException` → `Failed("请求超时，检查网络或调大超时时间")`
  - 其它 IO → `Failed("网络错误：<message>")`
- 用 `withContext(Dispatchers.IO)`，`HttpURLConnection`，`use {}` 关流。
- **不得**把 apiKey 打进任何日志。

---

## 4. 远程抓取 `data/kaoyan/KaoyanFetcher.kt`

```kotlin
class KaoyanFetcher {
    /**
     * 抓取一个 JSON 格式的院校目录并解析成 SchoolItem 列表。
     *
     * 期望的远端格式（与内置 assets/kaoyan/schools.json 完全一致）：
     * {"schools":[{"id":"...","name":"...", ... 同 SchoolItem 字段 ...}]}
     *
     * 之所以认这个格式：它就是本项目自己导出的格式，谁都能拿一份
     * schools.json 放自己服务器上，改完在设置里填 URL 就能刷新。
     */
    suspend fun fetchCatalog(url: String): FetchResult
}

sealed interface FetchResult {
    data class Ok(val schools: List<SchoolItem>) : FetchResult
    data class Failed(val message: String) : FetchResult
}
```

- 用 `withContext(Dispatchers.IO)`，`HttpURLConnection`，8 秒超时。
- 空 URL → `Failed("刷新地址为空")`
- 非 2xx → `Failed("服务返回 HTTP xxx")`
- JSON 解析失败 → `Failed("返回的不是合法 JSON")`
- `schools` 为空 → `Failed("返回的目录里没有院校条目")`
- 必须容忍缺字段（用 `optInt`/`optString` 缺省值）。

内置种子：`app/src/main/assets/kaoyan/schools.json`，**至少 40 条**（院校 × 专业），
覆盖 20 所不同院校、专业覆盖 `Major` 里至少 12 种、地区覆盖 7 个大区。
数据要**自洽**（报录比、分数线数字合理，不能全是 0），字段名与 `SchoolItem` 一一对应。
这是演示用的**样例数据**，JSON 顶部不要加注释（JSON 不支持），
可在 `note` 字段里写「示例数据」提示。

---

## 5. 持久化

### 5.1 Room 实体（加到 `data/model/Entities.kt` 末尾）

```kotlin
@Entity(tableName = "kaoyan_schools", indices = [Index("majorCode")])
data class KaoyanSchoolEntity(
    @PrimaryKey val id: String,
    val name: String,
    val province: String,
    val city: String,
    val tier: String,
    val majorCode: String,
    val majorName: String,
    val scoreLast: Int = 0,
    val scorePrev: Int = 0,
    val quota: Int = 0,
    val applicants: Int = 0,
    val mathRequired: Boolean = false,
    val tags: String = "",
    val note: String = "",
) {
    fun toDomain(): SchoolItem = SchoolItem(...)
    companion object {
        fun fromDomain(e: SchoolItem): KaoyanSchoolEntity = ...
    }
}
```

> `core.kaoyan.SchoolItem` 是**唯一**的模型来源，实体只做 Room 映射。

### 5.2 DAO（加到 `data/db/Daos.kt`）

```kotlin
@Dao
interface KaoyanSchoolDao {
    @Query("SELECT * FROM kaoyan_schools ORDER BY tier, name")
    fun observeAll(): Flow<List<KaoyanSchoolEntity>>

    @Query("SELECT * FROM kaoyan_schools WHERE majorCode = :code ORDER BY tier, name")
    fun observeByMajor(code: String): Flow<List<KaoyanSchoolEntity>>

    @Query("SELECT COUNT(*) FROM kaoyan_schools")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<KaoyanSchoolEntity>)

    @Query("DELETE FROM kaoyan_schools")
    suspend fun clear()
}
```

### 5.3 数据库 v2 → v3

`@Database(version = 3)`，entities 加上 `KaoyanSchoolEntity::class`，
`abstract fun kaoyanSchoolDao(): KaoyanSchoolDao`。

新增：

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 建表语句必须与 Room 生成的完全一致，否则 Room 打开时会报
        // 「Room cannot verify the data integrity」。
        // 以 build/generated/ksp/debug/kotlin/.../AppDatabase_Impl.kt 里的
        // createAllTables 为唯一准绳，逐字复制过来。
        db.execSQL("CREATE TABLE IF NOT EXISTS `kaoyan_schools` (...)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_kaoyan_schools_majorCode` ON `kaoyan_schools` (`majorCode`)")
    }
}
```

并在 `QauApp.kt` 的 builder 上追加 `.addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)`。

**这是硬性验收点**：建表 SQL 必须与 KSP 生成的一致，集成任务会逐字比对。

### 5.4 DataStore（`data/prefs/SettingsStore.kt` 追加）

`AppSettings` 新增：

```kotlin
val aiBaseUrl: String = "",
val aiApiKey: String = "",
val aiModel: String = "deepseek-chat",
val kaoyanCatalogUrl: String = "",
/** 收藏的院校 id，逗号分隔。 */
val kaoyanFavorites: String = "",
```

对应 `stringPreferencesKey("ai_base_url")` / `"ai_api_key"` / `"ai_model"` /
`"kaoyan_catalog_url"` / `"kaoyan_favorites"`。

新增方法：

```kotlin
suspend fun setAi(baseUrl: String, apiKey: String, model: String)
suspend fun setKaoyanCatalogUrl(url: String)
suspend fun setKaoyanFavorites(ids: List<String>)
```

`AppSettings` 里加便捷属性：
```kotlin
val aiConfig: AiConfig get() = AiConfig(aiBaseUrl, aiApiKey, aiModel)
val favoriteIds: List<String> get() = kaoyanFavorites.split(',').filter { it.isNotBlank() }
```

### 5.5 仓库 `data/repo/KaoyanRepository.kt`

```kotlin
class KaoyanRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
) {
    /** 首次打开时把 assets 里的种子灌进数据库；已有数据则跳过。 */
    suspend fun ensureSeeded(): Int

    fun observeSchools(): Flow<List<SchoolItem>>
    suspend fun schoolsNow(): List<SchoolItem>

    /** 从远端刷新；成功则整体替换本地目录。 */
    suspend fun refreshFromRemote(url: String): RefreshResult

    suspend fun askAi(question: String): AiResult

    suspend fun setFavorite(id: String, on: Boolean)
    suspend fun toggleFavorite(id: String): Boolean
}
```

`RefreshResult` 复用 `KaoyanFetcher.FetchResult` 的语义即可（`Ok(count)` / `Failed(msg)`），
定义成 `data/repo/KaoyanRepository.kt` 内的 `sealed interface RefreshResult`。

`askAi` 组装 system prompt：「你是考研规划助手，回答要具体、给数据、给来源，
不确定就说不确定。回答控制在 400 字以内。」+ 用户问题。

---

## 6. UI

### 6.1 入口

底栏 `Tab` 增加：`KAOYAN("考研", Icons.Filled.School, Icons.Outlined.School)`
—— 注意 `GRADES` 已占用 School 图标，改用 `Icons.Filled.AutoStories` /
`Icons.Outlined.AutoStories`。

`MainActivity.kt`：
- 新增 `import cn.edu.qau.timetable.ui.kaoyan.KaoyanScreen`
- `TabHost` 的 `when` 增加 `Tab.KAOYAN -> KaoyanScreen(vm, Modifier.fillMaxSize())`

### 6.2 `ui/kaoyan/KaoyanViewModel.kt`

```kotlin
class KaoyanViewModel(
    private val repo: KaoyanRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings>
    val schools: StateFlow<List<SchoolItem>>
    val favorites: StateFlow<Set<String>>
    val profile: StateFlow<KaoyanProfile>
    val recommendations: StateFlow<List<Recommendation>>
    val aiAnswer: StateFlow<AiState>       // Idle / Loading / Ok(text)
    val busy: StateFlow<Boolean>
    val toast: StateFlow<String?>

    init { /* ensureSeeded() */ }

    fun setMajor(m: Major); fun setRegion(r: Region); fun setAmbition(a: Ambition)
    fun setUndergraduateTier(t: SchoolTier); fun setCrossMajor(b: Boolean)
    fun setAcceptMath(b: Boolean); fun setMinQuota(n: Int)

    fun refreshCatalog(url: String)
    fun askAi(question: String)
    fun toggleFavorite(id: String)
    fun clearToast()
}

sealed interface AiState {
    data object Idle : AiState
    data object Loading : AiState
    data class Ok(val text: String) : AiState
}
```

`recommendations` 用 `combine(schools, profile) { s, p -> RecommendEngine.recommend(p, s) }`
`.stateIn(viewModelScope, WhileSubscribed(5000), emptyList())`。

`AiState` 定义放在 `KaoyanViewModel.kt` 文件内。

### 6.3 `ui/kaoyan/KaoyanScreen.kt`

主入口，内部用一个二级 `FilterChip` 行（或 `SegmentedButton`）切 4 个子页：

1. **选校** `KaoyanSchoolScreen(vm)`
2. **论文** `KaoyanSiteScreen(vm, SiteCategory.THESIS + PATENT)`
3. **资料** `KaoyanSiteScreen(vm, STUDY + COURSE + DATA)`
4. **AI** `KaoyanAiScreen(vm)`

复用现有视觉语言：
- 卡片用 `Card(Modifier.fillMaxWidth())`，内边距 12–14dp，间距 8dp
- 列表项入场用 `Modifier.staggeredAppear(index, animate = rememberEntranceWindow(list.size))`
- 列表 `contentPadding` 的 `bottom` 要加 `LocalFloatingBarReserve.current`
  （否则最后一条被悬浮底栏永久挡住）
- 空状态仿照 `SimpleScreens.kt` 里的 `private fun EmptyState(title, hint)`
  —— 那是 private 的，在 kaoyan 包里**自己再写一个**同名私有 composable，不要改原文件

### 6.4 `ui/kaoyan/KaoyanSchoolScreen.kt`

- 顶部一张「报考意向」卡片：专业 `FilterChip` 群（横向滚动）、地区、报考意愿、
  本科层次（`DropdownMenu` 或 chip 群）、两个 `Switch`（跨专业、接受数学）、
  最低招生人数（`OutlinedTextField`，数字键盘）
- 结果区：按 `Recommendation.tier` 分组，每组一个 `Text` 小标题（「冲刺 / 稳妥 / 保底」+ 条数）
- 每条卡片：院校名 + 层次 chip、`省份·城市`、专业名、
  「复试线 2025 / 2024」「招生 n / 报考 m」「报录比 x.x」、
  score 用 `LinearProgressIndicator(progress = { score/100f })` 可视化、
  理由逐条前置 `+`，警告逐条前置 `!` 并用 `MaterialTheme.colorScheme.error` 着色
- 收藏按钮 `IconButton`（`Icons.Filled.Star` / `Icons.Outlined.StarBorder`）
- 顶部右侧一个「刷新目录」`IconButton`（`Icons.Filled.Refresh`），
  从 `settings.kaoyanCatalogUrl` 拉取；URL 为空时 `toast` 提示去设置页填

### 6.5 `ui/kaoyan/KaoyanSiteScreen.kt`

- 传入 `categories: Set<SiteCategory>` 过滤 `SiteCatalog.all`
- 每条：站点名 + 分类 chip + 描述，右侧一个「搜索」按钮 →
  先弹一个 `AlertDialog` 输入关键词，确认后用
  `Intent(Intent.ACTION_VIEW, Uri.parse(SiteCatalog.searchUrl(site, q)))` 打开浏览器
- 再给一个「打开首页」的 `TextButton`
- 顶部一个全局搜索框：输入关键词后，把该分类下**所有**站点各生成一个 URL，
  以 `AlertDialog` 列表形式让用户挑一个打开 —— 这是「论文参考 / 学习资料」的
  核心交互：一次输入，多站直达

### 6.6 `ui/kaoyan/KaoyanAiScreen.kt`

- 顶部说明卡：告诉用户这是「需要自己填 API 的 AI 搜索」，
  未配置时显示引导文案 + 「去设置」按钮（`vm.settings` 判断 `aiConfig.isReady`）
- 一个 `OutlinedTextField`（多行，3 行高）+ 「提问」`Button`
- `AiState.Loading` → `LinearProgressIndicator` + 「AI 正在整理…」
- `AiState.Ok` → `Card` 里 `SelectionContainer` 显示回答，底部两个按钮：
  「复制」（`LocalClipboardManager`）
- 回答下方给一排「继续追问」`AssistChip`（如「推荐几所保底院校」「这个专业考什么科目」）
- 明确写一句提示：「AI 的回答仅供参考，分数线与招生计划请以研招网为准」

### 6.7 设置页（`ui/SettingsScreen.kt`）

在「抓取地址」卡片之后追加一张 **「AI 搜索」** 卡片：

- `OutlinedTextField`：base URL（placeholder `https://api.deepseek.com/v1`）
- `OutlinedTextField`：API Key（`visualTransformation = PasswordVisualTransformation()`）
- `OutlinedTextField`：模型名（placeholder `deepseek-chat`）
- `OutlinedTextField`：考研目录刷新地址（placeholder 留空说明可空）
- 「保存」按钮 → `vm.setAi(...)` / `vm.setKaoyanCatalogUrl(...)`
- 一段 `labelSmall` 说明：密钥只存本机 DataStore、不进仓库、不上传

`MainViewModel` 需新增：

```kotlin
fun setAi(baseUrl: String, apiKey: String, model: String) = safeLaunch { ... }
fun setKaoyanCatalogUrl(url: String) = safeLaunch { ... }
```

> `MainViewModel` 是唯一允许被修改的既有类。改动仅限追加上述两个方法 +
> 在构造函数里拿到 `KaoyanRepository`（由 `MainActivity` 的 `viewModelFactory` 传入）。

### 6.8 依赖注入

`QauApp.kt` 的 `AppContainer` 增加：

```kotlin
val kaoyan: KaoyanRepository = KaoyanRepository(context.applicationContext, db, settings)
```

`MainActivity` 里 `KaoyanViewModel` 用 `viewModel(factory = viewModelFactory {
    initializer { KaoyanViewModel(container.kaoyan) }
})` 创建 —— 在 `KaoyanScreen` 内部拿 `(LocalContext.current.applicationContext as QauApp).container`。

---

## 7. 单测（`app/src/test/java/cn/edu/qau/timetable/kaoyan/`）

至少三个套件，纯 JVM：

1. `RecommendEngineTest` —— 逐条验证 §1.7 的 7 条打分规则
   （专业匹配 ±、地区、层次差四档、数学、招生规模、报录比、分数带），
   外加：排序稳定性、`limit` 生效、三档都出现。
2. `SiteCatalogTest` —— 目录非空、id 唯一、`searchUrl` 正确替换并编码
   （含中文关键词、含空格、含 `&`）、每个 `homepage` 以 `http` 开头。
3. `SchoolSeedTest` —— 解析 `assets/kaoyan/schools.json` 需要 Android Context，
   **改为**测 `KaoyanFetcher` 的 JSON 解析逻辑：把解析函数抽成
   `internal fun parseCatalogJson(raw: String): List<SchoolItem>`，
   用一段内联 JSON 字符串测正常 / 缺字段 / 非法 JSON 三种情况。

---

## 8. 文档

- `README.md`：功能表加「考研」一行；架构树加 `core/kaoyan/`、`ui/kaoyan/`；
  已知限制里补一条「AI 搜索需要自备 API，App 不内置密钥」
- `CHANGELOG.md`：新增 `## [3.2] - 2026-10-17` 段
- `app/build.gradle.kts`：`versionCode 13` / `versionName "3.2"`

---

## 9. 实现偏差与裁决（captain 裁定，以此为准）

契约执行过程中出现的偏差，逐条记录裁定结果。**后续任务以本节为准**，
正文与本节冲突时以本节为准。

### D1. `Major` 枚举数量 = **20**

正文 §1.1 实际列出 20 项。分派时在任务描述里写成「21 个」是笔误。
实现为 20 项正确。**测试断言用 20，不要断言 21。**

### D2. `RecommendEngine.score` 为 `object` 成员函数

调用方式 `RecommendEngine.score(profile, school)`。符合契约「公开出来供单测逐项验证」的意图。

### D3. 新增派生属性 `SchoolItem.applyRatio: Double`

```kotlin
/** 报录比；数据不全时返回 0，调用方据此显示「未收录」而不是显示 0.0。 */
val applyRatio: Double
    get() = if (quota > 0 && applicants > 0) applicants.toDouble() / quota else 0.0
```

非新增存储字段，不影响任何构造调用，不破坏契约。UI 展示报录比直接用。
测试建议：断言 `applyRatio` 与 `applicants.toDouble() / quota` 一致。

### D4. `SiteCatalog` 实际 **28** 条

契约要求 >= 18，实现 28 条，5 个分类全覆盖。**测试断言用 `>= 18`，不要用 `== 18`。**

### D5. `searchUrl` 空查询行为 = 返回 `homepage`

契约正文未写明此边界。现明确为契约行为：

```kotlin
val keyword = query.trim()
if (keyword.isEmpty()) return site.homepage
```

理由：拼一个空关键词的搜索 URL 只会打开站点的空结果页，不如直接回首页。
**测试需补一个空查询用例。**

### D6. 规则 5 的执行顺序

`quota == 0` 必须先判定为「数据缺失」并跳过 `minQuota` 比较，
不能再落到「招生人数低于期望」的扣分分支 —— 否则用户把 `minQuota` 设为 1 时，
所有未收录人数的院校都会被误判为不达标。实现已按此处理，保留。

### D7. 规则 3 层次差的分支顺序

`delta < 0` 的判定必须**先于** `delta in 0..15`，否则负值会漏进「层次相当」的加分区间。
实现已按此处理（`when` 里 `delta < 0` 在首位），保留。

---

> 以下 D8–D12 由 qa-engine（测试与文档任务）在写单测时发现并记录，**不改契约正文**。

### D8. `recommend` 的排序主键是「画像意愿档优先」

§1.7 正文的排序描述是「先按 tier（REACH→MATCH→SAFE）顺序」，
同节 KDoc 又要求「结果按该档优先排序」。两句在字面上冲突。

实现取的是**两者合并**：排序键第一位是 `listOf(profile.ambition) + CANONICAL_ORDER.filter { it != profile.ambition }`
的索引 —— 即画像里选的那一档排最前，其余两档按 REACH→MATCH→SAFE 跟进。
这样既满足了「该档优先」，又保证同一份画像下输出顺序完全稳定。

裁定：**以实现为准**。理由：若严格按 REACH→MATCH→SAFE 走，
用户在「稳妥」档选出的结果会被埋在一片冲刺院校后面，`ambition` 字段就白设了；
而「顺序稳定」是 Compose 列表不整片闪烁的前提。
`RecommendEngineTest.排序先按档位再按分数降序` 与 `画像意愿档排在最前` 两条用例锁住这个行为。

### D9. `limit <= 0` 视为不限制

§1.7 只给了 `limit: Int = 60` 的默认值，未定义 0 与负数的语义。
实现为 `if (limit > 0) ordered.take(limit) else ordered` —— 传 0 返回全量而不是空列表。

裁定：保留。理由：调用方从配置里读到的 0 更可能是「没设置」而不是「要空列表」，
返回空列表会让整页无解释地空白。用例 `limit 为0时不限制条数` 锁住。

### D10. `KaoyanFetcher` 用手写 `MiniJson` 而不是 `org.json`

§0 把 `org.json` 列为可用依赖，但 Android 的 `org.json` 在**纯 JVM 单测**里是个
方法体直接抛 `RuntimeException("Stub!")` 的空壳 —— 数据层一旦依赖它，
§7 要求的 `parseCatalogJson` 就只能靠 Robolectric 或额外依赖才能测。

实现改为在 `data/kaoyan/KaoyanFetcher.kt` 内自带一份递归下降解析器（`internal object MiniJson`，
约 200 行，同时提供 `quote()` 给 AI 客户端拼请求体）。

裁定：保留。**未引入任何新依赖**，符合 §0 的硬约束；
且换来「解析逻辑在纯 JVM 上可测、且内置种子与远端刷新走同一条链路」。
`SchoolCatalogParseTest` 的 20 条用例直接覆盖这份解析器。

### D11. `parseCatalogJson` 的位置与可见性

§7 要求抽成 `internal fun parseCatalogJson(raw: String): List<SchoolItem>`。
实现是 `data/kaoyan/KaoyanFetcher.kt` 里的 **internal 顶层函数**（不是 `KaoyanFetcher` 的成员），
同模块内（含 `src/test/`）直接 `import cn.edu.qau.timetable.data.kaoyan.parseCatalogJson` 调用。

裁定：保留，符合契约意图 —— 顶层函数不需要先构造 `KaoyanFetcher` 实例，
也不需要协程作用域，测试里一行就能调。

### D12. 测试套件名：`SchoolSeedTest` → `SchoolCatalogParseTest`

§7 第 3 个套件原计划叫 `SchoolSeedTest`，但它实际测的是 JSON 解析而不是种子数据本身，
且 `SchoolSeedTest` 在 v3.1 之前已被用作临时校验脚本的类名（`TempSeedVerifyTest` 同类目的遗留命名）。

裁定：改名 `SchoolCatalogParseTest`，落在 `app/src/test/java/cn/edu/qau/timetable/kaoyan/`。

### D13. 中文路径下 `testDebugUnitTest` 全挂的根因

现象：在 `D:\Agent工作区\Android开发\qau-timetable` 直接跑 `testDebugUnitTest`，
**11 个套件全部** `initializationError / ClassNotFoundException`（连 v3.1 的老套件一起挂），
但报错说 `.class` 文件确实存在（`direct file exists=true`）。

根因：Gradle 的 test worker 用 **`@argfile`** 传 classpath（`@C:\Users\...\gradle-worker-classpath*.txt`）。
JVM 按 `sun.jnu.encoding`（本机 = GBK）解析 argfile 内容，而 Gradle 写的是 UTF-8，
于是含中文的 classpath 条目被解成乱码，`File.exists()` 返回 false → 类加载不到。

**决定性证据**：同一条 classpath 直接 `-cp` 传能加载，走 `@argfile` 就失败。
用 `*`（星号通配）、正斜杠、`-Dfile.encoding=UTF-8`、`JAVA_TOOL_OPTIONS` 都无效 ——
`sun.jnu.encoding` 在 JVM 启动时由 OS locale 硬定，命令行改不动。

两种可用绕法（二选一，**不要**把 `-Dfile.encoding=GBK` 塞进 test worker —— 源码里有大量
中文注释与中文断言文案，改编码有把断言字符串读坏的风险）：

1. **复制到 ASCII 路径跑**（本次采用，最稳）：
   `robocopy "<中文工程路径>" C:\qau-qa /E /XD build .gradle .kotlin .git`
   然后 `cd C:\qau-qa` 跑 `gradlew.bat --no-daemon testDebugUnitTest`。
2. 只把 **Gradle daemon** 的默认编码改成 GBK（test worker 仍保持 UTF-8）：
   `$env:GRADLE_OPTS='-Dorg.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=GBK'`
   实测此配置下 daemon 写出的 argfile 能被正确解析，199 用例全绿。

裁定：这是**环境问题，不是代码问题**。captain 已复现并独立验证：

- 实验 A：`--no-daemon --no-build-cache --rerun-tasks testDebugUnitTest`（无编码参数）
  → **BUILD FAILED，11 用例 11 失败**（全套件 `ClassNotFoundException`）
- 实验 B：同上 + `GRADLE_OPTS=...-Dfile.encoding=GBK`
  → **BUILD SUCCESSFUL，199 用例 0 失败**

**最终裁定：CI / 集成验证一律用方案 2（`GRADLE_OPTS` 设 GBK）。**
理由：方案 1 要复制工程，验证的不是当前工作区本身；方案 2 只影响 daemon 写 argfile 的编码，
test worker 的 `file.encoding` 仍是 UTF-8，中文断言不会被读坏（实测 208 用例全绿佐证）。
`GRADLE_OPTS` 是进程级环境变量，不会污染任何提交入库的文件。

---

## 10. 集成阶段发现并修复的产品缺陷（captain）

### F1. 推荐引擎三档形同虚设（严重）

**现象**：用真实种子数据跑推荐，默认画像（普通本科 / 计算机 / 稳妥）下
**67 条推荐全部落进 SAFE 档，最高分仅 45，冲刺与稳妥两档完全为空**。
用户明确选了「稳妥」，却一条稳妥都拿不到 —— 功能名义存在、实际不可用。

**根因**（三个问题叠加）：

1. **规则 3 的方向是反的**。原实现：`delta > 40 → -15`「跨度较大」。
   但冲刺档看的就是比自己高的学校，越符合「冲刺」语义的院校被扣得越多。
   而分档又要求 `score >= 75 && 层次高于本科` 才是 REACH —— 两者直接矛盾，
   REACH **在数学上不可达**。
2. **分档门槛 75/55 与分值上限不匹配**。单条理论满分为
   专业 30 + 地区 20 + 层次 15 + 招生 8 + 报录比 10 + 分数带 8 = 91，
   而最常见画像（专业对口 + 地区不限 + 层次相当）只能到 42~57。
   75 的门槛意味着绝大多数用户永远看不到「冲刺」。
3. **种子数据分布失真的**。计算机专业原有 7 条全是 211 及以上，
   报录比 8.1~15.8 —— 一个普通本科生查计算机，只能看到「保底 45 分」。

**修复**：

1. 规则 3 改为**按 `Ambition` 分派三套区间**：
   - REACH：`delta 16..45 → +18`（冲刺就该够高的），`delta < 0 → -10`（方向错了）
   - MATCH：`delta 0..15 → +15`，`16..30 → +5`，`> 30 → -10`
   - SAFE：`delta < 0 → +15`（更低才适合保底），`> 15 → -10`（比本科还高不适合保底）
2. 门槛重标定为 `REACH_THRESHOLD = 55` / `MATCH_THRESHOLD = 38`（提取为具名常量，
   KDoc 里写明标定依据），并保留「REACH 还要求层次高于本科」这条约束。
3. 种子数据从 67 条补到 **108 条 / 26 所院校 / 20 个专业 / 16 省份**，
   给每个专业补齐普通院校层级，使三档都有内容可推。

**回归保护**：新增 `SeedIntegrationProbeTest`（4 用例），用**真实 assets 文件**
驱动引擎，断言：稳妥档非空、冲刺意愿下冲刺档非空、保底意愿下保底档非空。
这条用例就是为了防止「三档全空」这个 bug 悄悄回归 —— 它是纯单测发现不了的，
必须喂真实数据才会暴露。

**教训**：契约里的分值是我写的，但**分值本身没人验算过可达性**。
以后凡是「打分 + 分档」的设计，交付前必须做一次
「给定典型输入，各档位能否被命中」的数值验证，而不只是测「函数返回了对的分」。

---

