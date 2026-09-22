# 青农课表（QAU Timetable）

适配**青岛农业大学**教务系统的 Android 课程表 App。

- 技术栈：Kotlin + Jetpack Compose + Room（minSdk 26 / targetSdk 34）
- 数据来源：**WebView 内登录**强智教务系统 `jwglxt.qau.edu.cn/jsxsd/`，注入 JS 抓取页面后本地解析
- **不保存密码**，所有数据只存本机

---

## 1. 功能

| 模块 | 说明 |
|---|---|
| 课表 | 周次切换（上一周/下一周/回本周）、合并单元格的课程网格、按课程名稳定配色、**左侧每节下方标出上下课时间** |
| 今日 | 当天课程列表，带真实上下课时间 |
| 考试 | 考试安排（日期 / 时间 / 考场 / 座位） |
| 成绩 | 成绩列表 + 总学分 + 加权平均分 |
| 教室 | 空闲教室查询结果 |
| 桌面小组件 | 今日课程；今天没课时显示最近一次课 |
| 上课提醒 | 按校区作息表提前 N 分钟提醒（AlarmManager 精确闹钟） |
| 设置 | 校区、学期第 1 周周一、总周数、提醒、抓取地址 |

### 校区作息（已内置，来自教务处官方《教学时间表》）

两个校区**上课时间不同**，所以必须选对校区：

| | 城阳校区 | 平度校区 |
|---|---|---|
| 上午预备 | 7:50 | 8:20 |
| 第 1 节 | 8:00–8:45 | 8:30–9:15 |
| 第 2 节 | 8:55–9:40 | 9:25–10:10 |
| 第 3 节 | 9:55–10:40 | 10:20–11:05 |
| 第 4 节 | 10:50–11:35 | 11:15–12:00 |
| **第 5 节** | **11:35–12:00** | **12:00–12:25** |
| 下午预备 | 13:50 | 13:50 |
| 第 6 节 | 14:00–14:45 | 14:00–14:45 |
| 第 7 节 | 14:55–15:40 | 14:55–15:40 |
| 第 8 节 | 15:55–16:40 | 15:50–16:35 |
| 第 9 节 | 16:50–17:35 | 16:45–17:30 |
| 晚上预备 | 18:40 | 18:40 |
| 第 10 节 | 18:50–19:35 | 18:50–19:35 |
| 第 11 节 | 19:45–20:30 | 19:45–20:30 |

来源：<https://jw.qau.edu.cn/content/wdxz/ed8334513205406e94eb41c305440aca>

课表网格**左侧每一节的下方**会直接标出该节的上课 / 下课时间
（例如第 1 节显示 `08:00` 与 `08:45`），按当前学期所属校区取用，
不用再回头对照上面这张表。

> 实现细节：`TimetableGrid` 原本没有接收 `campus` 参数，作息时间根本无从取用；
> 现已把 `campus` 透传进去，并给三个 `Text` 显式指定 `lineHeight` ——
> 否则会继承 `bodyLarge` 的 24sp 行高，三行 72dp 会撑爆 62dp 的格子。

---

## 2. 架构

```
app/src/main/java/cn/edu/qau/timetable/
├── core/                  纯 Kotlin，无 Android 依赖，可单测
│   ├── Campus.kt          校区
│   ├── PeriodTimes.kt     作息时间表（城阳/平度）
│   ├── WeekPattern.kt     "1-16周(单)" 这类周次语法的解析
│   └── Term.kt            学期与周次换算
├── domain/                UI 层用的模型（与 Room 实体解耦）
├── data/
│   ├── model/             Room 实体
│   ├── db/                DAO + Database
│   ├── prefs/             DataStore 设置
│   ├── qz/                强智教务：JS 注入、JSON 契约、表格解析
│   └── repo/              Repository（抓取结果入库）
├── ui/                    Compose 界面 + ViewModel
├── widget/                RemoteViews 桌面小组件
└── notify/                AlarmManager 上课提醒
```

### 抓取流程

```
WebView 打开 jwglxt.qau.edu.cn/jsxsd/
      ↓ 用户自己输账号/验证码（App 完全不接触）
      ↓ 注入 QzJs.EXTRACT_FN
      ↓ 把 <table> 摊平成等长二维数组（rowspan/colspan 复制填充，
        带 title 属性的元素编码成 "属性名\u0001值"）
      ↓ QauBridge(JavascriptInterface) 回传 JSON
QzTableParser 解析 → Repository 入库 → Room → UI
```

**为什么用 WebView 而不是直接 HTTP 登录**：强智的登录参数是 JS 加密的，还常常带验证码。
在 WebView 里让用户正常登录，既不碰密码，也不怕它改加密算法。

**解析为什么不依赖 CSS 类名**：强智各校部署模板差异很大，类名一改版就失效。
这里只依赖两件稳定的事——课表是「节次 × 星期」的表，单元格是换行分隔的文本。

---

## 3. 编译

### 常规环境（x86_64 Linux / macOS / Windows）

用 Android Studio 直接打开，或：

```bash
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

> 如果通过 `gradle wrapper` 生成 wrapper：`gradle wrapper --gradle-version 8.9`

### 在 aarch64 Linux 上编译（本仓库当前的环境）

Google **从未发布过 linux-aarch64 的 `aapt2`**（连 9.5.0-alpha 都没有），
所以 aarch64 主机必须用 QEMU 跑 x86_64 版 aapt2。本仓库已配置好：

- QEMU：`/opt/qemu/usr/bin/qemu-x86_64` + x86_64 sysroot `/opt/x86_64`
- 包装脚本：`/opt/qau-build/aapt2`
- `gradle.properties` 中的 `android.aapt2FromMavenOverride=/opt/qau-build/aapt2`

重建这套环境见 `setup-toolchain.sh`。若换机器，请把 `aapt2FromMavenOverride`
指向你自己的包装脚本，或在 x86_64 机器上删掉这一行。

---

## 4. 适配到别的学校 / 别的教务系统

1. **改入口**：`data/qz/QzEndpoints.kt` 里的 `BASE` 和各页面 URL。
2. **改作息**：`core/PeriodTimes.kt`，替换成你学校的节次时间。
3. **改解析**：如果目标不是强智，`QzTableParser` 的表头关键词和启发式需要调整，
   但「摊平成二维数组 → 文本解析」这条链路是通用的。
4. 也可以在 App 的「设置」里直接改抓取地址，不用改代码。

### 抓不到怎么办

App 里有「抓当前页」按钮和诊断信息（显示抓到的行列数、表头）。
典型排查顺序：

1. 先确认 WebView 里已经登录成功；
2. 手动点到课表页面，再点「抓当前页」；
3. 如果表头显示的不是课表，说明跳错了页面，去设置里改 URL；
4. 如果行数对但课程为空，把该页面的 HTML 存下来，对照 `QzTableParser` 调整启发式。

---

## 5. 测试

```bash
./gradlew :app:testDebugUnitTest
```

覆盖的是最容易出错、也最值得测的部分：

- `WeekPatternParserTest`：`1-16周` / `1-16周(单)` / `1-8,10-16周` / `第3—15周` / 解析失败兜底
- `PeriodTimesTest`：两个校区作息差异（尤其第 5 节）
- `QzTableParserTest`：rowspan 合并还原、教师/教室启发式、title 属性优先、
  一格多课、周次内嵌在课程名里、考试/成绩解析

---

## 6. 常见问题

### 登录时输入的字顺序颠倒（倒序）

症状：输入 `admin123`，框里显示成 `123admin` 之类。

**根因是 Android 7.0+ WebView 的已知缺陷，叠加了两个 WebView 设置：**

1. Android 7.0 起 WebView 由 Chrome 进程渲染，**它不绑定 App 的 locale**，
   会把 Activity 的 `locale` / `layoutDirection` 重置成设备默认值
   （[SO 40398528](https://stackoverflow.com/questions/40398528/android-webview-language-changes-abruptly-on-android-7-0-and-above)、
   [Google issue 109833940](https://issuetracker.google.com/issues/109833940)）。
   页面方向一旦被判成 RTL，字母与数字混排会被 bidi 重排 —— 看起来就是"倒序"。
2. 早期版本还开了 `useWideViewPort` / `loadWithOverviewMode`：
   软键盘弹出时控件尺寸变化会触发页面重新缩放，
   在输入法组词过程中重排，插入位置就乱了。

**已做的修复：**

1. `MainActivity.attachBaseContext` 把 locale 固定为简体中文（LTR）；
2. WebView 设 `textDirection = TEXT_DIRECTION_LTR`、`layoutDirection = LAYOUT_DIRECTION_LTR`；
3. 去掉 `useWideViewPort` / `loadWithOverviewMode`；
4. 去掉 manifest 里无用的 `android:supportsRtl="true"`；
5. 关闭 WebView 自动填充（它也会往输入框里插字符）；
6. 默认开启「输入方向修复」，向页面注入：

   ```css
   html,body{direction:ltr !important;}
   input,textarea{direction:ltr !important;unicode-bidi:plaintext !important;}
   ```

   青农大教务系统是中文站（LTR），强制 LTR 不会破坏它本来的排版。

**⚠️ 实测结论（2026-09 更新）：上面的 LTR 修复对"输入倒序"无效。**

也就是说，这不是方向/RTL 问题（至少不只是）。目前已变更的策略与诊断手段：

- **`windowSoftInputMode` 改为 `adjustPan`**：`adjustResize` 会在软键盘弹出时
  改变 WebView 尺寸，而尺寸变化会在输入法组词过程中触发重排，
  有可能导致插入位置错乱。`adjustPan` 下 WebView 尺寸不变。
- **「输入方向修复」默认关闭**：既然无效，就不该默认启用多引入变量；
  开关保留，方便做 A/B 对照。
- **去掉 WebView 的 `textDirection`**：那是 `TextView` 的 API，
  套在 WebView 上语义不明。
- **新增「导出页面HTML」**：抓取时把当前登录页的 HTML（截断到 20000 字符）
  显示在同步页，可长按复制。**这是定位输入问题的决定性材料** ——
  能直接看到登录页自己的输入处理脚本。

**自己先做个判定实验**：把账号在别处输好、复制，然后**长按网页里的输入框选「粘贴」**。

- 如果**粘贴正常、逐字输入倒序** → 问题在输入法组词/逐字插入这条链路上；
- 如果**粘贴也倒序** → 问题在页面本身或设备的文字方向设置上（那就是浏览器里也一样）。

**如果仍然颠倒**：请在「同步」页点「导出页面HTML」，
把 HTML（或截图）连同**手机型号 / Android 版本 / 输入法**一起发出来。

### 点击「跳转并抓取 / 抓当前页」闪退

已做的处理：

1. **所有对 WebView 的调用统一走 `safeEval` / `safeLoad`**：切主线程 + 兜异常 +
   检查自己的销毁标志（`WebView` 没有 `isDestroyed()`，得自己记）。
   之前直接调 `evaluateJavascript` / `loadUrl`，一旦 WebView 已销毁或页面没准备好
   就会抛异常直接闪退。
2. **JS 桥的 `post(json)` 参数改为可空**：JS 传 `undefined` 时，
   原来非空的 Kotlin 参数会触发空检查异常。
3. **崩溃日志落盘**：未捕获异常会写到应用私有目录，
   在「设置 → 上次崩溃日志」里可查看/复制/清除。

> ⚠️ 注意：WebView 的渲染进程是**独立进程**，它崩溃不会走上面的处理器。
> 那种情况必须用 `adb logcat` 才能抓到。

如果还闪退：请到「设置 → 上次崩溃日志」复制内容发出来；
日志为空的话，用 `adb logcat -b crash` 抓一下（`adb logcat -b crash -d > crash.txt`）。

### 一门两节的课，导入后变成两个单节

**根因**：判断"这门课占几小节"原本靠"相邻两格文本完全相同就合并"。
但强智在跨节的每一格里可能都写上节次（第 1 格写"第1节"、第 2 格写"第2节"），
两格原始文本就**不相同**，合并失败，一门两节的课裂成两个单节。
而 4 节的实验课如果是整块 `rowspan`，复制填充后文本相同，反而合并成功 ——
所以会出现"有机化学实验正常、其它课都变单节"这种看起来很奇怪的现象。

**修复**（三层，从权威到兜底）：

1. **以 `rowspan` 为准**：JS 侧额外回传一个与 `rows` 同形的 `rowspans` 网格，
   锚点处记录该格纵向跨几行。这是"占几小节"的权威依据，不再猜。
2. **文本合并改为归一化比较**：比较前把纯节次行（`第1节` / `1-2节` / `1`）去掉，
   这样"只差节次"的两格也能认成同一门课。
3. **单元格内的节次行只用来放宽范围**：若格子里另写了 `第1-2节`，
   只把范围取大，绝不缩短 —— 宁可显示长一点，也不要把课切碎。

另外把课表渲染的跨度上限从 4 提到 11，这样 4 节的实验课能完整撑开。

---

### 导入失败：`IllegalArgumentException: Cannot coerce value to an empty range: maximum 11 is less than minimum 12`

**这一条同时解释了之前的"点击抓取闪退"** —— 它们是同一个 bug 的两副面孔：

- 加异常兜底**之前**：解析抛异常 → 协程里没人接 → **进程直接死**（闪退）；
- 加异常兜底**之后**：同一个异常变成了这条可见的错误消息。

**根因**：节次范围钳制写反了顺序。

```kotlin
val s = minOf(rowStart, entry.startPeriod ?: rowStart)
val e = maxOf(rowEnd, entry.endPeriod ?: rowEnd)
startPeriod = s.coerceIn(1, PeriodTimes.PERIOD_COUNT),
endPeriod   = e.coerceIn(s, PeriodTimes.PERIOD_COUNT),   // ← s 是原始值，可能 = 12
```

`s = 12` 时 `coerceIn(12, 11)` 就是空区间，Kotlin 直接抛异常。

**为什么 `s` 会等于 12**：旧的节次推断假定**第 0 列就是节次列**，
读不出数字就用"上一行末尾 + 1"兜底。偏偏青农大课表首列不是节次（或首列读不到），
于是行号一路递增：表头算 1，第 12 行就变成 12。

**修复**：

1. **节次列 = 第一天所在列的左边一列**，不再假定是第 0 列；
2. 节次列确实读不出来时，兜底改为**按数据行序递增**（1..N，有界），不再带着表头一起涨；
3. 钳制顺序修正 —— 先把 `s` 收进 `[1, MAX_PERIOD]`，再用 `e.coerceIn(s, MAX_PERIOD)`，
   区间**永远不可能倒置**；
4. 课表渲染行数改为 `max(11, 数据中出现的最大节次)`，这样学校若真有第 12 节也不会被丢掉；
5. 新增 `QzTableParser.describe()`，同步页诊断里会打印
   `节次列=? / 星期列=[...] / 检出节次=1,2,3...`，一眼看出是哪一步错了。

**验证方式**：把旧的行号递增逻辑 + 旧的 `coerceIn` 临时还原，
新增的回归测试 `首列不是节次列时不能崩（复现 coerceIn 空区间）`
**确实以同一个 `IllegalArgumentException` 失败**；换回修复版后 34 个测试全过。

---

### 课表把两节课压成一节（对比学校企业微信课表发现）

**症状**（对着学校企业微信的课表逐条比对）：

| 课程 | 真实范围 | 修复前显示在 |
|---|---|---|
| 概率论与数理统计 | 1–2 节 | 第 **2** 节（单节） |
| 有机化学实验 | **1–4 节** | 第 **2** 节 + 第 **4** 节（裂成两块） |
| 习近平新时代… | 3–4 节 | 第 **4** 节（单节） |
| 有机化学 A505 等 | 6–7 节 | 第 **7** 节（单节） |
| 仪器分析 A501 | 8–9 节 | 第 **9** 节（单节） |

规律：**每门课都被压到真实范围的最后一节，且只剩单节。**

**根因**：上一版为了修"跨节"引入 rowspan 支持时，写了这么一条分支：

```kotlin
declaredSpan > 1  -> ... 用 rowspan
declaredSpan == 1 -> r        // ← 直接当单节，不合并
else              -> ... 按文本合并
```

JS 侧现在总会回传 `rowspans` 网格，于是 `useSpans` 恒为真（因为 1 也 > 0）。
**而强智经常根本不写 `rowspan`，而是把同一门课在同一列的相邻格里重复写一遍** ——
这时每一格的 span 都是 1，全部走了"直接当单节"分支，合并被彻底关掉。
（4 节的实验课被切成 1-2 / 3-4 两块，也是同一原因。）

**修复**：只信任 `declaredSpan > 1`；`span <= 1` 时**一律**回到归一化文本合并。
两种表格结构（rowspan 式 / 重复单元格式）现在都能正确处理。

顺带修了一个会在表头识别失败时**让整张表串一位**的问题：
若 `headerRow < 0`，改用「节次列第一次出现 1」定位数据起始行。

**验证**：把 `declaredSpan == 1 -> r` 这一行临时加回去，
新增的 `span=1 的相邻同文本格子仍要合并` 和 `四节的实验课不会裂成两块`
**两个测试都失败**；移除后 37 个测试全过。

同步页新增「**复制抓取结果（反馈用）**」按钮：一键把抓到的原始表格
（含每格文本与 spans）复制到剪贴板，反馈问题时粘贴即可。

---

### 真正的根因：强智用「第1,2节」而不是「第1-2节」

用户把抓到的原始表格 dump 出来后，一眼就看清了。节次列实际长这样：

```
r1: 第1,2节     r2: 第3,4节     r3: 第5节
r4: 第6,7节     r5: 第8,9节     r6: 第10,11节     r7: 备注:
```

**强智用逗号分隔节次。** 而早期的 `parsePeriod` 只认减号：

```kotlin
PERIOD_RANGE  = Regex("""(?:第)?\s*(\d{1,2})\s*[-~～—–至]\s*(\d{1,2})\s*节""")
PERIOD_SINGLE = Regex("""(?:第)?\s*(\d{1,2})\s*节""")
```

`第1,2节` 不匹配 RANGE，却**匹配了 SINGLE 里的 `(\d{1,2})节` → 捕获「2」**，
于是整门课塌成单节。诊断输出 `检出节次=2,4,5,7,9,11,12` 完美印证：
1-2→2、3-4→4、6-7→7、8-9→9、10-11→11，而 `备注:` 走兜底递增变成了 12。

> 这也解释了为什么前几轮都没修对：我一直在猜表格的**结构**（rowspan / 合并），
> 但真正的问题在**文本格式**上。没有原始数据就只能瞎猜 —— 所以这次加了
> 「复制抓取结果」按钮。

**顺带暴露的第二个问题**：青农大的表是**一行一个「节次组」**
（1-2 / 3-4 / 5 / 6-7 / 8-9 / 10-11），不是一行一节；而且最后有一行 `备注:`。

**修复**：

1. `parsePeriod` 改为取「节」字之前的**所有**数字，返回 `(min, max)`：
   逗号、顿号、减号、波浪号、`至`、全角括号全部兼容；
2. 节次列可用时，**标签非空但读不出节次的行走 (0,0) 跳过**，不再拿"上一行+1"兜底
   —— 否则 `备注:` 会变成第 12 节的一堆假课（这正是 `coerceIn(12,11)` 那个异常的来源）；
3. `PERIOD_ONLY_LINE` 同样兼容逗号写法；
4. 节次 title 只认「含节且不含周」的键 —— 强智的周次 title 写作 `周次(节次)`，
   里面也含"节次"，直接用 `findKeyed` 会误取到周次值。

**修复后的解析结果**（与学校企业微信逐条一致）：

```
d1 1-2 [概率论与数理统计] r=[城阳B202]      d1 3-4 [习近平新时代…] r=[城阳A107]
d1 6-7 [毛泽东思想和…]   r=[城阳A107]      d1 8-9 [形势与政策]   r=[城阳A104]
d2 3-4 [有机化学]        r=[城阳A509]      d3 3-4 [体育Ⅲ]       r=[城阳排球场b]
d4 1-4 [有机化学实验]    r=[化学楼331]     ← 跨两组自动合并成一整块
d4 6-7 [仪器分析(全英文)] r=[城阳A407]      d5 6-7 [有机化学]     r=[城阳A405]
```

**验证**：把 `parsePeriod` 还原成"只认减号"的旧版，
`真实青农大课表：逗号写法 第1,2节 必须解析成 1-2`
**立即失败**；换回修复版后 38 个测试全过。

---

### 输入倒序（未完，但已定位到"不是网页的锅"）

先纠正一个之前的错误认知：青农大教务系统**不是直接登录强智**，而是走
**金智教育统一身份认证（CAS）**：

```
jwglxt.qau.edu.cn/jsxsd/  →  authserver.qau.edu.cn/authserver/login?service=...
```

登录表单是这三个框：`#un`（学号）/ `#pd`（密码）/ `#code`（动态码）。

**把登录页的 HTML / JS / CSS 全部拉下来逐行看过之后：**

| 检查项 | 结果 |
|---|---|
| `login10.js` 对 `#un` `#pd` 的处理 | **只做回车提交 + 隐藏错误提示**，完全不碰输入内容 |
| `login2.css` 是否有 `direction` / `rtl` / `unicode-bidi` | **一条都没有** |
| 输入框属性 | 标准 `<input>`，仅 `autocomplete="off"` |
| 切换登录方式 | 用 `$("#login_content").html(passwordhtml)` 重建表单（只在点标签时） |

**结论：倒序不是网页造成的**，出在 **WebView ↔ 输入法** 这条链路上。
复现环境（来自一份 Android bugreport，此处已去掉可定位到具体设备的信息）：

| | |
|---|---|
| 设备 / 系统 | 某国产 ROM 手机 / **Android 17 (API 37)** |
| WebView | Chromium **150.x** |
| 输入法 | 该 ROM 定制的第三方中文输入法 |

（之前走的"Android 7 WebView 篡改 locale"那条路是错的 —— 那个 issue 早就修了，
在 Android 17 + WebView 150 上不适用，这就是 LTR 修复无效的原因。）

**已做的处理**

1. **新增「手动填入」**（推荐先用这个）：在 App 的**原生输入框**里输入学号/密码/动态码，
   再点「填入网页」，由 JS 写进 `#un` / `#pd` / `#code`。动态码看网页上的图片即可。
   这条路径**完全不经过 WebView 的输入法**，所以它一定是正常的 ——
   既能当绕过方案，也能反过来确认问题确实在输入法链路上。
2. **真正关掉自动填充**：改用 `IMPORTANT_FOR_AUTOFILL_NO`。
   之前用的 `NO_EXCLUDE_DESCENDANTS` 含义是"本视图不重要但后代仍考虑"，
   **并没有关掉**，自动填充仍可能往输入框里插字符。
3. 去掉 `layoutDirection` / `textDirection`（实测无益，只会多引入变量）。

**还需要的判定实验（30 秒）**：用手机自带浏览器（Chrome）打开同一个登录页，
输入看是否也倒序。

- **也倒序** → 是设备/输入法/WebView 的问题，与 App 无关（可换输入法验证）；
- **不倒序** → 是 App 的 WebView 配置问题，再逐项二分。

---

### 4 节的「有机化学实验」渲染成空白框

逗号节次修好之后其余课程都对上了，只剩周四的 `有机化学实验`（1-4 节）
渲染成一个**灰框：位置和跨度都对，但没有文字**。

- **数据侧已确认没问题**：用你抓到的真实表格在本地跑单测，
  解析结果就是 `d4 1-4 name=[有机化学实验] t=[曲] r=[化学楼331]`。
- 所以问题在**渲染**。

**根因**：课表原本按「行」排布 —— 每个节次一个固定高度的 `Row`，
跨节的课靠 `Modifier.requiredHeight()` 让盒子**溢出父容器**来撑高度。
这条路径在真机上不可靠（盒子比父容器高，绘制/裁剪行为依赖具体实现），
表现就是"框画出来了，文字没了"。

**修复**：整个课表改成**按列排布**：

```
Row {
    节次列 Column { 1..N 各一个固定高度 Box }
    周一列 Column { 按 period 遍历：有课 → 高度 span*CELL_HEIGHT 的盒子；
                    被覆盖 → 跳过；没课 → 固定高度 Spacer }
    ... 周二..周日同理
}
```

跨度直接由**盒子自身高度**表达，不再有任何溢出：
每列总高严格等于 `periodCount * CELL_HEIGHT`，跨行天然对齐，
也就不存在"被后面的行盖住或裁掉"的可能。

另外两处保险：

1. **课程格显式指定文字颜色**。Material3 的 `Surface(color = 自定义色)` 会把
   `contentColor` 推成 `contentColorFor(自定义色)`，自定义色不在主题
   `colorScheme` 里时返回 `Color.Unspecified`（其 `alpha` 是 NaN），
   有让文字不可见的风险。
2. **名字为空时显示 `(未命名)`**，避免再出现"看上去是空框"而无从判断。

顺带把「复制抓取结果」升级为**同时导出原始表格和解析出来的事件**：

```
--- parsed events (15) ---
d4 1-4 name=[有机化学实验] t=[曲] r=[化学楼331] w=[2-16(周)]
```

---

### 输入倒序：已定位到"我的 WebView 配置"

用户实测：**同一个登录页在手机自带浏览器里输入正常，在本 App 的 WebView 里倒序。**
这就把范围锁死在「我的 WebView 与 Chrome 的差异」上。

先排除了一条：用 **WebView 的 UA** 重新抓登录页，和 Chrome UA 拿到的内容
**逐字节相同**（唯一差异是每次请求都变的 `lt` 登录票据）——
服务器没有做 UA 分流，不是"给我们发了不同的页面"。

**找到的最大差异**：我此前把 `useWideViewPort` / `loadWithOverviewMode` 去掉了。

页面有 `<meta name="viewport" content="width=device-width, initial-scale=1">`。
**不设 `useWideViewPort` 时，WebView 会忽略这个 meta**，按默认的 980px 宽布局页面，
再整体缩放塞进控件宽度。于是输入框的坐标都落在"缩放空间"里，
中文输入法跟随光标组词时算出的插入位置就会错位 —— 表现就是输入倒序。
**Chrome 是尊重 viewport meta 的，所以同一个页面在浏览器里正常。**

修复（与浏览器对齐）：

```kotlin
settings.useWideViewPort = true
settings.loadWithOverviewMode = true
settings.javaScriptCanOpenWindowsAutomatically = true
// 并且移除了 IMPORTANT_FOR_AUTOFILL_NO —— Chrome 是有自动填充的，
// 显式关掉反而制造了差异
```

**⚠️ 实测结论：viewport 那套改动也没修好，倒序依旧。**

于是换了个做法 —— 不再猜，而是**把 App 里所有"浏览器没有、我加了"的东西全删掉**：

| 删掉的东西 | 为什么可疑 |
|---|---|
| `attachBaseContext` 里强制 `Locale.SIMPLIFIED_CHINESE` | Chrome 从不强制 locale，这是我凭空加的一层 |
| `enableEdgeToEdge()` | 边到边 + 输入法 inset 的处理是 WebView 输入异常的可疑路径 |
| `windowSoftInputMode="adjustPan"` | 改回浏览器语义的 `adjustResize` |
| `IMPORTANT_FOR_AUTOFILL_NO`、`textDirection`、LTR 注入 | 之前已逐步移除 |

同时补上了**唯一能区分两种病因的判据**。「诊断输入方向」以前只回传
`direction` / `unicode-bidi`，现在还会回传输入框里的**真实值**：

- `#un` 的值**本身就是反的** → 输入法 / `InputConnection` 插入位置错乱（编辑器侧）
- `#un` 的值**是对的、只是显示反了** → bidi 方向问题（渲染侧）

这两条路的修法完全不同，此前一直在没区分的情况下换方案，所以反复落空。

**保底可用路径**：「手动填入」面板现在**默认展开**——
用 App 的原生 `OutlinedTextField` 输入学号/密码/动态码，再由 JS 注入
`#un`/`#pd`/`#code`，**完全不经过 WebView 的输入法**，
所以无论根因是哪一种都绕得过去。填入后还会**回读网页里的真实值**并显示出来，
用于确认注入没被页面改写。

---

### UI：Material 3 Expressive

**工具链升级**（为了拿到 material3 1.4.0，即 Expressive 的正式版）：

| | 之前 | 现在 |
|---|---|---|
| Kotlin | 1.9.24 | **2.0.21**（Compose 编译器改由 `kotlin.plugin.compose` 提供） |
| AGP | 8.5.2 | **8.7.3** |
| compileSdk / targetSdk | 34 | **35** |
| Compose BOM | 2024.06.00 | **2025.06.01** |
| material3 | 1.2.1 | **1.4.0** |
| Room / KSP | 2.6.1 / 1.9.24-1.0.20 | **2.7.1 / 2.0.21-1.0.28** |

> aarch64 上的 `aapt2` 仍走 QEMU 包装脚本，已切到 build-tools 35 并验证可用。

**Expressive 的落地**（先用 `javap` 查过 1.4.0 的真实 API，没有凭记忆写）：

- **完整色调板**（浅色 + 深色），包含 Expressive 强调的
  `surfaceContainerLowest…Highest` 分层表面色；
- **Expressive 形状刻度**：`ShapeDefaults` 里出现了
  `LargeIncreased` / `ExtraLargeIncreased` / `ExtraExtraLarge` / `CornerFull`，
  即 Expressive 的定义性特征。圆角从 classic 的 4/8/12/16/28
  提到 **4/8/12/20/32**；
- **更粗的字体**（title / label 用 SemiBold）；
- 课表格的圆角改为跟随主题刻度，不再写死。

**底部导航图标**：之前是个明显缺陷 —— `icon = { Text(label) }` 且同时传了 `label`，
等于**文字显示了两遍**。现在改成 Material 图标，选中用 filled、未选中用 outlined：

| 标签 | 选中 | 未选中 |
|---|---|---|
| 课表 | `Icons.Filled.DateRange` | `Icons.Outlined.DateRange` |
| 今日 | `Icons.Filled.Today` | `Icons.Outlined.Today` |
| 考试 | `Icons.Filled.Assignment` | `Icons.Outlined.Assignment` |
| 成绩 | `Icons.Filled.School` | `Icons.Outlined.School` |
| 教室 | `Icons.Filled.Place` | `Icons.Outlined.Place` |

**没能用上的部分（如实说明）**：`MaterialShapes`（饼干/四叶草那类装饰形状）、
`ButtonGroup`、`FloatingToolbar`、`LoadingIndicator` **不在 material3 1.4.0 里**
（属 1.5.0-alpha）。我核对过 AAR，1.4.0 的 `classes.jar` 里没有这些类，
所以没有把不存在的 API 写进代码。

---

### 成绩页显示课程编号而不是课程名称（+ 重复记录）

**根因**：强智的成绩表**同时有「课程编号」和「课程名称」两列**，都含"课程"。

```
序号|开课学期|课程编号|课程名称|成绩|绩点|学分|总学时|考核方式|课程属性|课程性质|考试性质
```

而旧实现写的是 `headerIndexOf(headers, "课程")` —— 取到**先出现的「课程编号」**，
于是整页显示的都是 `4040001`。

**修复**：新增 `courseNameColumn()`，优先精确匹配「课程名称 / 科目名称 / 课程名」，
否则取"含课程/科目、但不含编号/代码/编码"的列。

**顺带修掉一个连带 bug**：`ingestGrades` 之前只 `insertAll` 不删，
**每抓一次就多存一份** —— 你截图里 52 门课有一大半是重复的（4040001 两条、
4040002 两条…）。现在按学期**先删后插**。

**验证**：把 `iCourse` 还原成旧写法，新增的
`成绩表要取课程名称而不是课程编号` **立即失败**，报错正是
`expected:<马克思主义基本原理> but was:<4040001>`；换回修复版后 39 个测试全过。

---

### 动态取色（Material You）

主题新增 `dynamicColor` 开关，默认**开启**：

```kotlin
val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    darkTheme -> DarkColors
    else -> LightColors
}
```

- Android 12（API 31）及以上：**从手机壁纸生成配色**，跟随系统深浅色；
- 更低版本或用户关掉开关：回落到内置的农大绿配色（不会崩）。

设置页「外观 → 跟随壁纸取色」可以切换 —— 因为动态色的主色由壁纸决定，
不一定和课表格子的配色搭，留个开关更稳妥。

### 安装时报「签名校验不通过」

**原因几乎总是：手机上已经装了一个用别的证书签名的旧版本。**
Android 不允许用不同证书覆盖安装同一个包名，安装器就会报签名类错误。

本项目早期版本确实有这个坑：`debug` 包用 AGP 自动生成的 `Android Debug`
证书，`release` 包用 `qau-release.jks`，两者证书不同 ——
所以 debug 和 release **互相覆盖安装必然失败**。

现已统一：两个变体都用 `qau-release.jks`
（见 `app/build.gradle.kts` 中 `buildTypes.debug.signingConfig`）。

处理办法：**先卸载手机上已有的旧版本，再装新的**。卸载会清掉已抓取的课表数据，
重装后重新同步一次即可；此后 debug / release 可以互相覆盖安装。

确认两个包证书是否一致：

```bash
apksigner verify --print-certs qau-timetable-release.apk
apksigner verify --print-certs qau-timetable-debug.apk
```

两个 `Signer #1 certificate SHA-256 digest` 应当完全相同。

---

## 7. 已知限制

- **强智页面改版会导致抓取失败**。这是这类 App 的固有风险，所以内置了「抓当前页」
  和诊断信息，方便快速定位。
- **考试/成绩/空闲教室的页面路径是强智常见默认值**，青农大的实际部署可能不同
  （青农大另有一套 `jwglxt2.qau.edu.cn` 的 PHP 系统）。请以设置页里可改的 URL 为准。
- 学期第 1 周周一需要在设置里手动填（或从教务系统抓），否则按月份粗略估算。
- 桌面小组件刷新依赖系统调度（最短 30 分钟）+ 导入数据后主动刷新。

---

## 8. 免责声明

- 本 App 是**个人自用工具，不是学校官方应用**。
- 学校明确说明「微信企业号移动平台」是目前**唯一官方移动端渠道**，
  因此请勿将本 App 对外分发或上架。
- 请只用自己的账号登录；App 不保存密码、不上传任何数据。
- 请勿高频请求教务系统，避免给学校服务器造成压力。

---

## 9. 许可

本项目以 **GNU General Public License v3.0** 发布，全文见 [LICENSE](LICENSE)。

需要说明的是：本 App 只是一个**个人使用的课表客户端**，
通过页面抓取读取使用者本人有权访问的教务数据，不包含、也不提供任何校方数据。
请遵守学校的信息系统使用规定。
