# Rust 重构可行性报告

**日期**：2026-11-04
**对象**：青小农（QAU Timetable）Kotlin + Jetpack Compose Android App
**结论**：**不建议整体重构**；core 层可选做局部移植，但收益很小。

---

## 一、先看事实：代码分布在哪儿

```
ui          15 文件   5,613 行   ← Compose
data        11 文件   2,695 行   ← Room / DataStore / 网络
core        22 文件   2,104 行   ← 纯算法（唯一可移植）
notify      11 文件   1,280 行   ← AlarmManager / Notification
MainActivity  1 文件     777 行   ← Compose 宿主
widget       1 文件     244 行   ← RemoteViews
domain       2 文件      54 行
util         1 文件      55 行
────────────────────────────────
合计        64 文件  12,875 行
```

**Rust 能碰的只有 core 的 2,104 行，占 16.4%。**

而且这 16.4% 里还有一大半是**静态数据**：

| 文件 | 行数 | 实际内容 |
|---|---|---|
| `Major.kt` | 491 | 330 个专业的枚举 + 代码 |
| `SiteCatalog.kt` | 345 | 28 个站点的 URL 模板 |
| `Region.kt` / `ResearchSite.kt` / `SchoolTier.kt` 等 | 164 | 常量表 |

把这些搬去 Rust，本质是**把 Kotlin 的 `enum` 改成 Rust 的 `enum`**，零性能收益，纯增维护成本。

真正有算法逻辑的只有：

| 文件 | 行数 | 内容 |
|---|---|---|
| `RecommendEngine.kt` | 335 | 7 条打分规则 + 排序 |
| `WeekPattern.kt` | 100 | 周次语法解析（正则） |
| `GradeSummary.kt` | 93 | 学分绩点加权 |
| `PeriodTimes.kt` | 90 | 作息时间表 |
| `DayOverride.kt` | 85 | 调休换算 |
| `SilenceWindow.kt` | 84 | 静音时段合并 |
| `MajorSearch.kt` | 75 | 四路字符串匹配 |
| `Term.kt` | 60 | 周次换算 |
| **合计** | **922** | **占全套代码 7.2%** |

---

## 二、为什么其余 83% 动不了

### Compose 是 Kotlin 编译器插件

`@Composable` 函数不是普通函数 —— Kotlin 编译器会重写它们的签名（插入 `Composer`、`$changed` 参数）、生成重组作用域、做稳定性推断。这套机制**没有跨语言 ABI**。Rust 写不出 `@Composable`，也调不动另一个语言写的 Compose 组件。

想让 UI 用 Rust，只有两条路：
- **Slint / Dioxus**：换成别的 UI 框架重写全部界面（5,613 行，且这套界面的动效系统 `Motion.kt` 是专门为 Compose 调的弹簧物理，换框架等于推倒重做）
- **保留 Compose，Rust 只做计算** —— 也就是下面推荐的做法

### notify 绑定 Android 框架

`AlarmManager`、`NotificationChannel`、`BroadcastReceiver`、`RingerModeController` —— 全是 Android 平台 API。Rust 要通过 JNI 调它们，等于**在 Rust 里写 Java 胶水**，比现在直接用 Kotlin 繁琐得多，且没有任何收益（这些 API 本身是 IPC，不是计算）。

### data 层大部分是框架绑定

Room 的 `@Entity` / `@Dao` 是注解处理器生成代码，DataStore 是 Android 的 Flow 封装。真正纯逻辑的只有 JSON 解析（`KaoyanFetcher` 的 MiniJson，12.8 KB）—— 这一块 Rust 确实有成熟库（serde_json），但当前实现已经够用。

---

## 三、性能真相：core 层根本不是瓶颈

这是本报告最关键的一节。项目里有实测记录（`app/build/perf-bench.txt`），JVM 中位数：

| 操作 | 数据规模 | 耗时 |
|---|---|---|
| `recommend`（默认画像） | 240 条 → 60 条 | **0.02 ms** |
| `recommend`（跨考，最坏情况） | 240 条全量打分 | **0.10 ms** |
| 真实种子 `recommend` | 870 条 | **约 0.03 ms** |
| 分组 `buildRecommendRows` | 30 张卡 | **0.01 ms** |
| 同上，加 `remember` 后 | 30 张卡 | **0.00 ms** |

**0.02 毫秒。** 一帧是 16.7 毫秒（60fps）。这些计算只占一帧的 **0.12%**。

之前用户报的"进考研页掉帧"，真正原因是**玻璃底栏每帧整屏录屏**（已修复：改成只录底栏那条底带），以及**列表项无条件挂 `graphicsLayer`**（已修复：不播动画时直接返回）。都跟 core 层的算法无关。

### 那 Rust 能快多少？

理论上 Rust 比 JVM 快，但要看在什么场景：

- **冷启动/JIT 未预热**：Rust 有优势（无需预热）。但 core 层首次调用也只要几毫秒，用户感知不到
- **稳态热循环**：Android 上的 ART 有 AOT 编译，32 位整数运算、数组遍历这类代码，**Rust 和 ART 差距通常在 1.5~3 倍以内**，不是数量级
- **本项目实际情况**：单次调用 0.02ms，就算快 3 倍变成 0.007ms，**用户完全无法感知**

反过来，JNI 调用本身有开销（每次跨语言调用约 **几十到几百纳秒**，还要加上参数封送）。对于 0.02ms 的操作，JNI 开销可能占掉收益的一大半。

---

## 四、如果一定要做，怎么做才不亏

**唯一合理的方案：`core` 里挑单一算法，Rust 实现 + JNI 接入，用现有单测验证正确性。**

选 `RecommendEngine` 的理由：
- 335 行，是 core 里最大的真算法
- **有 55 条单测**钉死了 7 条规则的确切分值，移植对错可验证
- 输入输出是纯数据（`KaoyanProfile` + `SchoolItem` 列表 → `Recommendation` 列表），JNI 边界清晰

### 工程结构

```
qau-core-rs/                     ← 新增 Rust crate
├── Cargo.toml
└── src/
    ├── lib.rs                   ← JNI 导出
    ├── profile.rs               ← KaoyanProfile 镜像
    ├── school.rs                ← SchoolItem 镜像
    └── engine.rs                ← 7 条规则

app/src/main/java/.../core/kaoyan/
    └── RecommendEngineNative.kt ← JNI 声明 + 降级回 Kotlin
```

### 关键风险

| 风险 | 说明 | 缓解 |
|---|---|---|
| **JNI 边界数据封送** | `List<SchoolItem>` 跨语言要序列化。如果每次调用都转 JSON 再解析，开销**远大于**省下的 0.02ms | 用 FlatBuffers / 手写 `jlong` 数组，或干脆把整个 candidate 列表缓存成 Rust 侧对象 |
| **构建复杂度** | Gradle 要加 `cargo ndk` 任务，CI 要装 Android target（4 个 ABI：arm64/armeabi-v7a/x86/x86_64） | 只编 arm64-v8a（覆盖绝大多数真机），体积从 19KB 起 |
| **APK 体积** | Rust 静态链接 libc 部分，单个 ABI 约 200~400KB | 开 LTO + strip + panic=abort 可压到 100KB 以内 |
| **双份实现** | 移植后 Kotlin 版要保留（作为 fallback 和单测基准），两份代码会漂移 | 单测同时跑两边、断言结果一致 |
| **调试体验** | Rust panic 跨 JNI 会变成 `abort`，栈信息难读；Android Studio 对 Rust 调试支持有限 | `catch_unwind` + 转 Java 异常 |

### 工作量估算

| 阶段 | 时间 |
|---|---|
| 装工具链（rustup + Android target + cargo-ndk） | 30 分钟（**注意：本机 winget 下载 rustup 已失败一次，网络受限**） |
| 移植 RecommendEngine + JNI 绑定 | 3~4 小时 |
| Gradle 集成（cargo 任务、ABI 打包） | 2~3 小时 |
| 双实现一致性测试 | 1~2 小时 |
| **合计** | **约 1 个工作日** |

换来的是：**0.02ms → 可能 0.01ms**，以及多一条构建链路、多一份要同步维护的代码。

---

## 五、建议

### 不建议做整体重构

理由不是"Rust 不好"，而是**这个 App 的复杂度不在计算上**：

- 83% 的代码是 Android 平台绑定（Compose / Room / AlarmManager），Rust 进不去
- 剩下 17% 里，一半是静态数据
- 真正的算法层单次耗时 0.02ms，占一帧的 0.12%，**不构成任何性能问题**
- 已经修掉的性能问题（玻璃录屏、动画常驻）都是渲染层，不是计算层

整体重构的代价是**几个月的重写 + 全部功能重测 + 丢失现有的 282 条测试积累**，换来的是用户完全感知不到的差异。

### 什么情况下值得考虑 Rust

如果将来出现这些需求，Rust 的价值才会显现：

1. **本地全文检索**：要离线搜几千本书/论文摘要，需要倒排索引 + 模糊匹配 —— Rust 的 `tantivy` 有压倒性优势
2. **大量数据同步**：要解析并合并几 MB 的教务系统原始 HTML/JSON，Kotlin 的 GC 压力会显现
3. **跨平台核心**：将来要出 iOS / 桌面版，把 core 抽成 Rust 可以真正复用（而不是像现在只能给 Android 用）
4. **图像/音频处理**：比如课表截图识别（OCR）、导出的 PDF 排版

### 当下更值得做的事

如果目标是「让 App 更快、更稳」，优先级应该是：

1. **启动速度**：现在冷启动要初始化 Room + DataStore + 三个 Service，可以用 Baseline Profile 优化（已有 `profileinstaller` 依赖，但没配置规则）
2. **首屏渲染**：课表页第一次进要等数据加载完才画，可以加骨架屏
3. **内存占用**：`Major.kt` 的 330 个枚举在 class 初始化时就全部构造，改成按需加载可以省几百 KB
4. **保持测试覆盖**：282 条单测是资产，继续加比换语言更有价值

---

## 六、附：本机环境现状

| 项 | 状态 |
|---|---|
| JDK 17 | ✅ `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot` |
| Android SDK / NDK | ✅ NDK 27.0.12077973 / 29.0.13846066 |
| Rust (rustc/cargo/rustup) | ❌ **未安装** |
| rustup 下载 | ⚠️ winget 首次尝试失败（`InternetOpenUrl() 0x80072ee7`），直连 static.rust-lang.org 超时 |

**安装 Rust 的实际障碍是网络**，不是技术。如果要做，建议先解决镜像（`rsproxy.cn` 在本机也不通），或手动下载离线安装包。
