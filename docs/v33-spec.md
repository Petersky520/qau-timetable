# v3.3 任务规范 —— 专业扩充 / 性能优化 / DeepSeek 网页版接入

本文件是 v3.3 的**唯一契约**。所有并行开发以此为准，不得自行改签名。

构建命令（JDK 17 不在 PATH）：

```powershell
cd "D:\Agent工作区\Android开发\qau-timetable"
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
```

**单测必须这样跑**（中文路径 + Gradle @argfile 编码问题，见 `docs/kaoyan-spec.md` §9 D13）：

```powershell
$env:GRADLE_OPTS='-Dorg.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=GBK'
.\gradlew.bat --no-daemon --no-build-cache testDebugUnitTest
```

---

## 任务 1：专业目录扩充（Major 20 → 覆盖全学科门类）

### 1.1 目标

当前 `core/kaoyan/Major.kt` 只有 20 个专业，集中在计算机 + 农学，用户反馈「不够全」。
扩到**覆盖教育部 14 个学科门类**、每个门类下有代表性的热门专业，
**总数不少于 90 个**。这是选校推荐的第一输入，专业少 = 功能窄。

### 1.2 硬性要求

```kotlin
enum class Major(
    val label: String,
    val code: String,
    val mathRequired: Boolean,
    /** 所属学科门类，UI 按这个分组展示，避免 90 个 chip 平铺成一堵墙。 */
    val category: MajorCategory = MajorCategory.OTHER,
)
```

新增 `core/kaoyan/MajorCategory.kt`：

```kotlin
/** 教育部学科门类。选校页按门类分组展示专业，否则 90 个 chip 无法浏览。 */
enum class MajorCategory(val label: String) {
    PHILOSOPHY("哲学"),
    ECONOMICS("经济学"),
    LAW("法学"),
    EDUCATION("教育学"),
    LITERATURE("文学"),
    HISTORY("历史学"),
    SCIENCE("理学"),
    ENGINEERING("工学"),
    AGRICULTURE("农学"),
    MEDICINE("医学"),
    MANAGEMENT("管理学"),
    ART("艺术学"),
    MILITARY("军事学"),
    CROSS("交叉学科"),
    ;
}
```

- 保留现有 20 个枚举项的名字与 code **完全不变**（有测试和种子数据依赖），
  只补 `category` 参数。
- 新增项 code 用真实教育部学科代码（学硕 4 位，专硕 4 位，如 `0854` 电子信息）。
- `mathRequired` 按该专业考研实际情况判断（农学/医学/文科类多为 false）。
- `fromName` 容错逻辑不变，兜底仍是 `COMPUTER`。
- **每个门类至少 4 个专业**，工学/管理学/医学/文学应更多。

### 1.3 种子数据同步扩充

`assets/kaoyan/schools.json` 从 108 条扩到**不少于 240 条**，要求：

- 每条 `majorCode` 必须能在新版 `Major` 里找到（不能有孤儿 code）
- 每个新增专业至少 **2 所院校**
- 层次分布保持梯度：C9 / 985 / 211 / 双一流 / 普通院校 都要有
- 数字自洽：`quota > 0`、`applicants > 0`、`scoreLast` 与 `scorePrev` 合理
  （学硕 260~400，农学可低至 250，专硕按门类）
- `id` 唯一，格式 `<院校代码>-<专业代码>`

### 1.4 UI 适配

`KaoyanSchoolScreen.kt` 的专业 chip 区改成**按门类分组**：

- 门类用一行横向滚动的 `FilterChip` 选中（或 `TabRow`），下面是该门类下的专业 chip
- 默认选中 `Major.COMPUTER` 所在的门类
- 不能 90 个 chip 一次性平铺（会卡，也是性能任务要解决的）

---

## 任务 2：性能优化（进入考研页掉帧）

### 2.1 已确认的根因

**主因：玻璃底栏每帧整屏录屏。** `MainActivity.kt` 第 463-470 行：

```kotlin
if (settings.glassEffect.blursBackdrop) {
    Modifier
        .onGloballyPositioned { backdropOrigin = it.positionInRoot() }
        .drawWithContent {
            contentLayer.record { this@drawWithContent.drawContent() }
            drawLayer(contentLayer)
        }
}
```

这段挂在整个内容层上，**每一帧**都把整屏录进 `GraphicsLayer` 再画出来。
考研页内容重（意向卡 + 分三组的结果卡 + 进度条），叠上整屏录屏直接掉帧。

**次因（考研页自身）：**

1. `KaoyanSchoolScreen` 每次重组都用 `buildList { Ambition.entries.forEach { recs.filter {...} } }`
   重建整个 `rows` 列表 —— O(3N) 过滤 + 分配，**没有 remember**。
2. `RecommendCard` 里 4 处 `LinearProgressIndicator`（每张卡一个），
   `progress = { score.score / 100f }` 用 lambda 版本会在每帧读取状态，
   且 `LinearProgressIndicator` 自带无限动画规范，滚动时持续重组。
3. `itemsIndexed(rows)` **没有 key**，列表更新时 Compose 无法复用。
4. `staggeredAppear(index + 1, animate = entrance)` 给每一项挂了动画修饰符，
   滚动时每次进入组合都在跑 `Animatable`。
5. `Recommendation` / `SchoolScore` 是 data class 但没标 `@Immutable`，
   Compose 视为不稳定类型，父级重组会连带重组所有卡片。

### 2.2 必须做的优化（逐条验收）

**A. 玻璃录屏按需降级（最关键）**

`MainActivity.kt`：

- 录屏**只有在底栏真正可见时才需要**。当 `overlay != Overlay.NONE`
  （同步页 / 设置页推入）时底栏不可见，必须跳过录屏。
- 考研页是 tab，底栏可见 —— 但要让录屏**不与滚动争抢**：
  用 `graphicsLayer` 的 `compositingStrategy` + 把录屏改为仅在需要时刷新。
  实现方式：录屏条件加上 `!isScrolling` 或改用 `Modifier.drawWithCache`。
- **降级策略**：新增设置项「性能模式」（`AppSettings.performanceMode: Boolean`），
  开启时玻璃效果强制降级为纯色（不录屏、不模糊）。默认**跟随设备**：
  用 `ActivityManager.isLowRamDevice` 判断，低内存设备默认开。

**B. `rows` 用 `remember` 缓存**

```kotlin
val rows = remember(recs) {
    buildList { ... }
}
```

**C. `itemsIndexed` 加 key**

```kotlin
itemsIndexed(rows, key = { _, row -> when (row) {
    is RecommendRow.Header -> "h-${row.tier.name}"
    is RecommendRow.Item -> "i-${row.rec.school.id}"
} })
```

**D. `LinearProgressIndicator` 换成轻量自绘**

不要用 Material3 的 `LinearProgressIndicator`（它带 indeterminate 动画规范，
且 lambda progress 版本会每帧读状态）。改用：

```kotlin
// 一条 4dp 高的圆角矩形，按分数比例画 —— 零动画、零重组、零语义开销
Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(trackColor)) {
    Box(Modifier.fillMaxWidth(score / 100f).fillMaxHeight().background(barColor))
}
```

**E. 数据类标 `@Immutable`**

给 `SchoolItem`、`SchoolScore`、`Recommendation`、`KaoyanProfile` 加
`@Immutable`（`androidx.compose.runtime.Immutable`）。

⚠️ 但 `core/kaoyan/` 是**纯 Kotlin 零依赖**层，不能 import Compose。
解决办法：把 `Recommendation` / `SchoolScore` 的展示拷贝做成 UI 层的
`@Immutable data class SchoolCardUi(...)`，在 ViewModel 里映射一次。
**不要**为了 `@Immutable` 往 core 层塞 Compose 依赖。

**F. `staggeredAppear` 只在首次入场**

滚动复用时不要再跑动画。`rememberEntranceWindow` 已经会在入场窗口结束后
返回 false，确认 `animate = entrance` 的 `entrance` 在滚动时不重新变 true。

**G. 开硬件加速**

`AndroidManifest.xml` 的 `<application>` 显式加
`android:hardwareAccelerated="true"`（默认就是 true，但显式声明可防止
某些 ROM / 主题覆盖）。

**H. 基准测量**

必须给出优化前后的**可复现**证据。做法：写一个 `androidTest` 或
`macrobenchmark` 太重 —— 改为**在 ViewModel 加计时日志**：

```kotlin
// 用 android.os.SystemClock，只在 debug 构建里打
val t0 = SystemClock.elapsedRealtime()
val recs = RecommendEngine.recommend(p, list)
Log.d("KaoyanPerf", "recommend ${list.size} -> ${recs.size} in ${SystemClock.elapsedRealtime()-t0}ms")
```

报告里贴出：240 条目录下 recommend 耗时。同时报告 `rows` 重建是否已消除
（用 `remember` 后同一 recs 引用不再重算）。

---

## 任务 3：DeepSeek 网页版接入（免 API）

### 3.1 现实约束（必须先读）

DeepSeek 网页版 `chat.deepseek.com` **没有公开免授权接口**。要"免 API 免费搜索"，
可行路径只有一条：**用 WebView 加载网页版，把用户的提问注入输入框并读取回答**。

这与本 App 已有的 `SyncScreen.kt` 是**同一套技术**（WebView + JS 注入 +
`@JavascriptInterface` 桥），复用那套模式即可，风险与限制也一致：

- 网页改版会让注入失效 → 必须给「兜底手动操作」路径，不能只有自动注入
- 登录态存在 WebView 的 cookie 里，App 不碰密码
- **不保证**能稳定解析回答（DOM 结构随时会变）→ 解析失败要能优雅降级

### 3.2 实现要求

新增 `ui/kaoyan/DeepSeekWebScreen.kt` + `data/kaoyan/DeepSeekWebBridge.kt`。

**模式**：`KaoyanAiScreen` 顶部加两个 `FilterChip`：
「网页版（免费）」/「API（自备密钥）」，默认选网页版。

网页版页面的结构：

1. `AndroidView { WebView }`，`javaScriptEnabled = true`、`domStorageEnabled = true`，
   `userAgent` **改成桌面 Chrome UA**（移动 UA 会进移动版页面，结构不同更难解析）
2. 首次加载 `https://chat.deepseek.com/`
3. 顶部一个原生 `OutlinedTextField` 输入问题 + 「发送」按钮
   —— 和 `SyncScreen` 一样，**issue：输入走原生框再注入**，
   避免 WebView 输入法倒序问题（该 App 已实测过这个系统缺陷）
4. 注入脚本 `QzJs` 风格：

```javascript
// 找到输入框（contenteditable 或 textarea），设值并派发 input 事件
function fill(text) {
  var el = document.querySelector('textarea') ||
           document.querySelector('[contenteditable="true"]');
  if (!el) return false;
  if (el.tagName === 'TEXTAREA') {
    var setter = Object.getOwnPropertyDescriptor(
      window.HTMLTextAreaElement.prototype, 'value').set;
    setter.call(el, text);
  } else {
    el.textContent = text;
  }
  el.dispatchEvent(new Event('input', { bubbles: true }));
  return true;
}
```

5. 发送：先找 `button[type=submit]` 或含「发送 / Send」文案的按钮点击；
   找不到就提示用户手动点
6. **读回答**：用 `MutationObserver` 监听消息容器，把最后一条
   assistant 消息的 `innerText` 回传。给一个「读取当前回答」按钮做手动兜底。
7. 所有 JS 注入都要 `runCatching` 包裹，失败只提示不崩

**不做**：不注入 cookie 到别处、不上传用户登录态、不绕过任何付费墙。

### 3.3 与 API 模式共存

`KaoyanAiScreen` 变成：

```
[网页版（免费）] [API（自备密钥）]
  ↓ 选中哪个就渲染哪个
DeepSeekWebScreen(vm, ...)  /  AiApiPane(vm, ...)
```

API 模式保留现有实现不动。

---

## 4. 验收总则

- `assembleDebug` 通过
- 全量单测通过（含新增用例）
- 不改既有 20 个 Major 枚举名与 code
- `core/kaoyan/` 仍零 android/androidx import（**注意任务 2-E 的约束**）
- 报告里必须贴出：专业总数、种子条数、recommend 耗时、单测套件数/用例数

---

## 5. 实现偏差（交付后回填，正文不改）

本节只记录**实现与上文契约不一致的地方**及理由，方便下次评审知道哪些是刻意的。
正文保持原样。

### 5.1 `Major.code` 允许 6 位（契约 §1.2 只写了 4 位）

契约原话是「新增项 code 用真实教育部学科代码（学硕 4 位，专硕 4 位）」。
实际实现里有 **10 个 6 位二级学科代码**（`010101` 马克思主义哲学、`050103` 汉语言文字学、
`060201` 史学理论及史学史 等）。

**原因**：哲学（7 项）、历史学（5 项）这类门类的一级学科总数就不到 4 个，
按契约同时要求「每门类至少 4 个专业」和「4 位代码」是**互斥的**。
选择的解法是补二级学科代码（仍是教育部目录里的真代码），并在 `Major.kt` 的类 KDoc、
`MajorCatalogTest.专业代码是4位或6位数字` 里都写明。
不采用「自造 4 位代码」——那会让 `majorCode` 和真实招生目录对不上，
用户拿这个代码去研招网搜不到东西。

### 5.2 `Major.category` 不给默认值（契约 §1.2 的签名写了 `= MajorCategory.OTHER`）

契约给的签名是 `val category: MajorCategory = MajorCategory.OTHER`。
实现去掉了默认值，改成必填。

**原因**：`OTHER` 在 `MajorCategory` 里**不存在**（14 个门类是教育部的全集），
留一个默认值等于允许「新增专业忘了归类」在编译期蒙混过关，然后在 UI 上消失。
去掉默认值后漏填会直接编译失败。同理 `fromName` 的兜底落 `ENGINEERING`
而不是 `OTHER`（理由写在 `MajorCategory.kt` 的 KDoc 里）。

### 5.3 种子数据 463 条而非 240+ 的「刚好够」

契约 §1.3 要求「不少于 240 条、每个新增专业至少 2 所院校」。
实际交付 **463 条 / 152 个专业 / 173 所院校 / 31 个省份**，
每个专业 ≥ 2 所院校，普通院校占比 38%。

**原因**：240 条摊到 152 个专业只剩 1.6 条/专业，会直接违反「每专业 ≥ 2 所」。
要满足后者下限就是 304 条起步，再补层次梯度到 463。

### 5.4 性能模式默认值：契约说「低内存设备默认开」，实现是「跟随设备」

`AppSettings.performanceMode` 的**结构体默认值仍是 `false`**，
真正的设备判定在 `SettingsStore` 构造时用 `ActivityManager.isLowRamDevice` 做。
保留 `false` 是为了让纯 JVM 单测里的 `AppSettings()` 不需要 Android Context。

### 5.5 契约 §2.2-E 的「给 SchoolItem 标 `@Immutable`」没有照做

契约原文让给 `SchoolItem` / `SchoolScore` / `Recommendation` / `KaoyanProfile` 加
`@Immutable`，随后又补了一句「但 core 层不能 import Compose」——两者冲突。
实现按后一条执行：新增 UI 层 `ui/kaoyan/KaoyanCardUi.kt` 里的 `@Immutable data class
KaoyanCardUi`，在 ViewModel 里映射一次。`core/kaoyan/` 仍零 Android/Compose import。

### 5.6 新增测试对契约数字用了下限断言

契约 §1.1 / §1.3 写「不少于 90」「不少于 240」，测试里就断言 `>= 90` / `>= 240`，
**没有**断言 `== 152` / `== 463`。原因：扩目录是后续还会做的事，
`==` 会在每次正常扩容时误报。真正防退化的是 `MajorCatalogTest.原有20个专业的枚举名
与code未被改动`（逐条比对）和 `SeedCatalogIntegrityTest.每条majorCode都能在Major枚举里找到`
（防孤儿）——这两条才是硬护栏。

### 5.7 「院校+专业组合不重复」是交付过程中新发现的真数据 bug

`schools.json` 原有 `10435-0857`，其 `majorCode` 是 `0854`，与 `10435-0854`
指向同一院校同一专业。`id` 不同 → Room 主键不报错 → 但选校页出两张一模一样的卡。
契约没有覆盖这个维度，是写 `SeedCatalogIntegrityTest` 时发现的，已删除该条并加断言防回归。
同理，`mathRequired` / `majorName` 与 `Major` 定义的一致性**只钉旧 20 项**：
新增专业里学硕/专硕共用 4 位 code 时写更细的展示名是合法的，全量卡死会误判。

### 5.8 契约 §2.1 没写的第 8 条次因：专业匹配是软打分

契约把掉帧归因于「玻璃录屏 + 考研页自身的 5 条」，那是**性能**层面的诊断，没错。
但扩种子到 463 条后暴露出一个**语义**层面的缺陷，契约完全没预料到：

`crossMajor = false` 时专业不对口只扣 25 分，而「地区不限 +12 / 招生规模 +8 /
报录比友好 +10 / 层次相当 +15」足以把别的专业堆到 28 分 —— 默认画像（计算机）下
UI 前 60 条里 **45 条是作物学/植物保护/兽医学**。用户没勾跨考，却满屏别的专业。

**修法（超出契约范围，已实现）**：`recommend()` 打分前先按 `majorCode` 过滤候选集。
不在契约里，是因为写契约时种子只有 108 条、对口院校够多，问题被数据规模掩盖了。
教训与 §10 的「三档全空」同源：**「打分 + 排序」类设计必须在接近真实的数据规模上
验证一次分布**，只测函数返回对不对是发现不了的。

副作用（正向）：默认画像下 `recommend` 从 0.17 ms 降到 0.03 ms ——
候选集从 463 缩到 15，这正是「软打分改硬筛选」顺带带来的性能收益。

### 5.9 契约 §4 的验收清单已全部满足

- `assembleDebug` 通过（`app-debug.apk` 19.1 MB，versionCode 14 / versionName 3.3）
- 全量单测通过：**16 套件 / 267 用例 / 0 失败**（含新增 `MajorCatalogTest` 17、
  `SeedCatalogIntegrityTest` 22、`MajorSearchTest` 18、`PerfBenchProbeTest` 2）
- 既有 20 个 Major 枚举名与 code 未改（逐条断言在 `MajorCatalogTest`）
- `core/kaoyan/` 仍零 `android` / `androidx` import（已用 `grep -E '^import (android|androidx)'` 复核）
- 数字已回填：专业 152 / 种子 463 / recommend 0.03 ms（真实种子）/ 16 套件 267 用例

