# 3.5 性能优化测量记录

这一页是「优化前后各跑一次、拿同一把尺子量」的原始记录。不是基准测试的替代品，
而是**可复现的静态证据**：Compose 编译器按类判定稳定性、按函数判定能否跳过，
这两件事直接决定「父级重组时有多少工作被白做」。再补一条运行时可测的几何用例。

复现命令（`-PcomposeMetrics` 由 `app/build.gradle.kts` 里的 `composeCompiler {}` 消费）：

```powershell
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_OPTS='-Dorg.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=GBK'
.\gradlew.bat --no-daemon --rerun-tasks :app:compileDebugKotlin -PcomposeMetrics
```

产物落在 `app/build/compose_compiler/`，本目录下的四个文件是两次运行的副本：

| 文件 | 内容 |
|---|---|
| `before-composables.txt` | 优化前：每个 composable 是否 restartable / skippable |
| `before-classes.txt` | 优化前：每个类被判定为 stable / unstable |
| `after-composables.txt` | 优化后，同口径 |
| `after-classes.txt` | 优化后，同口径 |

## 结论一：可跳过的 composable 从 40 → 50，且**没有任何一个不可跳过**

| | 优化前 | 优化后 |
|---|---|---|
| restartable | 40 | 50 |
| 其中 skippable | 40 | 50 |
| restartable 但 **not** skippable | 0 | 0 |

数量增加 10 个是设置页拆卡的直接结果（一张 `SettingsScreen` 拆成 10 张卡），
不是新增功能。关键指标是「不可跳过的函数 = 0」：优化后**没有**引入任何一个
「父级重组就必然跟着重组」的函数。

⚠️ 但这一栏必须先读结论三再解读：Kotlin 2.0.21 默认开着 Strong Skipping，
所以「skippable = 1」不等于「参数稳定、一定跳得掉」。它能跳，但 unstable 参数
走引用相等 —— 真正的判据是「传进去的对象是不是同一个」。结论三有对照实验。

## 结论二：应用自己的**数据模型**没有一个是「可避免的」unstable

`classes.txt` 里共有 38 个 unstable 类。其中 7 个是 Room/KSP 生成代码
（`*Dao_Impl`、`AppDatabase_Impl`），4 个是手工 DI / Activity / Application 容器
（故意持有可变引用），剩下的是**数据类**，且它们 unstable 的原因只有一个：
字段里出现了 Compose 无法证明不可变的类型 —— `LocalDate`、`LocalTime`、`Set<Int>`、`List`。

代表例子（都能在 `after-classes.txt` 里逐条对上）：

| 类 | 唯一的 unstable 字段 | 为什么不动它 |
|---|---|---|
| `WeekPattern` | `weeks: Set<Int>` | core 层纯 Kotlin，加 `@Immutable` 就要引 Compose |
| `CourseEvent` | `pattern: WeekPattern`（被上面传染） | 同上；它是课表格子的入参，77 个格子共享同一批实例 |
| `Term` | `startMonday: LocalDate` | 同上 |
| `ClassPeriod` | `start` / `end: LocalTime` | 同上 |
| `AppSettings` | `dayOverrides: List<DayOverride>` | 设置快照，改动频率是「用户点一下」级别 |

**为什么不给它们加 `@Immutable`**：`core/` 是纯 Kotlin 零依赖层，单测在纯 JVM 里跑，
这是 README §2 和 v3.3 契约 §4 明确写下的架构约束。用 Compose 注解换稳定性
等于用「编译器的保证书」换架构分层，而且这些类里确实有可变集合（`Set` / `List`
在 Kotlin 语义下不是不可变的），打了注解反而不诚实。

## 结论三：Strong Skipping 是默认开着的 —— 别把 unstable 当成掉帧主因

这条是**做出来的实验**，不是推断。Kotlin 2.0.21 的 Compose 编译器默认启用
Strong Skipping（含 `OptimizeNonSkippingGroups`），它的效果是：

- 参数里有 unstable 类型时，函数**仍然可以是 skippable**；
- 但 unstable 参数的相等判断退化成**引用相等（`===`）**，不再是 `equals()`；
- lambda 自动 memoize（所以「每次重组新建 lambda」不再是白送一次重组）。

对照实验（同一份优化后代码，只切 `featureFlags StrongSkipping.disabled()`）：

| | Strong Skipping 开（默认，发布形态） | 关（实验） |
|---|---|---|
| restartable | 50 | 50 |
| **skippable** | **50** | **23** |
| `CourseCell`（参数含 unstable `CourseEvent`） | restartable **skippable** | restartable **不可跳过** |
| `CourseRow`（同上） | restartable **skippable** | restartable **不可跳过** |

**怎么读这个结果**：

1. 优化后 50/50 全部可跳过，其中 31 个虽然带 unstable 参数、靠 Strong Skipping
   才可跳过。所以「加 `@Immutable`」在这个工程里的收益远小于直觉预期 ——
   默认配置已经把这块兜住了。
2. 但它兜的是**能否跳过**，不是**是否相等**。unstable 参数走 `===`：
   只要每次传进去的是新实例，照样重组。这才解释了为什么本次优化的重点不是
   「标 `@Immutable`」，而是**别在组合期新建实例**：
   - `TimetableScreen` 以前每次重组都 `term?.toDomain()` 新建 `Term`，
     而它是三个 `remember` 的 key —— 现在 `remember(term)` 只转一次；
   - 列表数据一律 `remember(...)`，保证传给子项的 list / 对象是同一个引用。
3. 也就是说：**真正该修的是「每帧新建对象」和「每帧多画一遍」，不是注解。**

结论：剩下这 38 个 unstable 类都不是本次掉帧的成因，逐条复核后没有可做的
低风险改动。真正的成本在「组合期的工作量」和「逐帧的图层/动画」，不在这里。



## 结论四：底带几何是可断言的（`BackdropBandTest`，7 条用例）

录屏从「整屏」改成「只录底栏背后那一条」是这次最大的改动，它的正确性不靠肉眼，
而是由 `ui/glass/BackdropBand.kt` 提供三个纯函数、由 `BackdropBandTest` 钉住：

- 底带贴着内容层底部，`bandTop + bandHeight == contentHeight`；
- 内容比底带矮时不产生负值（横屏 + 键盘弹起）；
- **录像原点与玻璃采样位置互补**：底栏顶边在录像坐标系里恰好是 0。
  这一条是「模糊内容整体错位」的唯一防线。

另外断言了底带面积 < 整屏的 20%（优化的全部意义在此）。

## 没有做的事（说明清楚，避免误读）

- **没有采集真机帧率/GfxInfo**。本机 Android 模拟器（API 36.1 x86_64 镜像）上
  应用能安装成功，但 `cmd package resolve-activity` 始终返回 `No activity found`，
  `am start` 报 `Activity class does not exist`，即使 `dumpsys package` 里
  该 Activity 明明存在。属镜像/包管理器状态问题，与应用无关；尝试过冷启动、
  wipe-data、重装、`pm enable` 均无效。因此没有 `dumpsys gfxinfo` 的帧统计数字。
- **没有做 macrobenchmark**。它需要一条稳定的设备/模拟器管线，这里的价值
  （给出可复现的 jank 百分比）抵不过新增 `androidTest` 基础设施的维护成本。
- 因此本页只声明「减少了什么工作」，不声明「提升了多少帧」。
  帧率请以真机 `adb shell dumpsys gfxinfo cn.edu.qau.timetable framestats` 复核。
