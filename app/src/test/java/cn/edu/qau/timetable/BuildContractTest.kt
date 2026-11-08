package cn.edu.qau.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 构建与首屏行为的静态契约。
 *
 * ## 为什么这些要用单测钉住
 *
 * 这三项改动（延迟初始化、骨架屏三态、Baseline Profile）都是**性能/体验**改进，
 * 共同特点是：**改坏了也不会让任何功能报错**。
 *
 *   - `AppContainer` 改回同步构造 → 功能全对，只是启动慢几十毫秒
 *   - `courses` 丢掉 `loaded` 标记 → 功能全对，只是冷启动会闪一下空状态
 *   - `baseline-prof.txt` 被删/格式写错 → 构建可能仍然成功（写错会被静默忽略）
 *
 * 这类退化只有靠"盯住文件长什么样"才能发现，所以用源码级断言兜住。
 *
 * 注意：这些断言读的是**源码文件**，属于工程契约测试，不是行为测试。
 * 它们不替代真机上的启动耗时测量，只是防止有人无心把它们删掉。
 */
class BuildContractTest {

    private fun src(relative: String): String {
        // 单测的 working dir 是 app/，源码在 src/main/...
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
        )
        val f = candidates.firstOrNull { it.exists() }
            ?: error("找不到 $relative，cwd=${File(".").absolutePath}")
        return f.readText(Charsets.UTF_8)
    }

    /**
     * AppContainer 的关键成员必须是 `by lazy`。
     *
     * 直接构造的话，它们会在 `Application.onCreate` 里同步跑完 ——
     * 那是冷启动路径，用户正在等第一屏。
     */
    @Test
    fun `AppContainer 用 by lazy 延迟初始化`() {
        val text = src("src/main/java/cn/edu/qau/timetable/QauApp.kt")

        // 这 6 项是启动时最重的，必须延迟
        for (name in listOf("db", "settings", "repo", "kaoyan", "reminders", "silence")) {
            assertTrue(
                "AppContainer.$name 应是 by lazy（否则会在 onCreate 里同步构造，拖慢冷启动）",
                Regex("""val\s+$name\s*:\s*[^=]+by\s+lazy""").containsMatchIn(text),
            )
        }
    }

    /**
     * 课表首屏必须是三态，不能把"还没读到"当成"没有数据"。
     *
     * 退化成两态的症状：冷启动时先闪一下「还没有课表数据」，
     * 对明明有课表的用户像是数据丢了。
     */
    @Test
    fun `课表首屏有三态而不是两态`() {
        val screen = src("src/main/java/cn/edu/qau/timetable/ui/TimetableScreen.kt")
        val vm = src("src/main/java/cn/edu/qau/timetable/ui/MainViewModel.kt")

        assertTrue("MainViewModel 应暴露 timetableLoaded", vm.contains("timetableLoaded"))
        assertTrue("TimetableScreen 应读取 loaded 状态", screen.contains("vm.timetableLoaded"))
        assertTrue("应有骨架屏分支", screen.contains("TimetableSkeleton"))
        assertTrue(
            "骨架屏判断必须在空状态之前（否则空状态仍会先闪）",
            screen.indexOf("if (!loaded)") < screen.indexOf("EmptyTimetable()"),
        )
    }

    /**
     * Baseline Profile 存在且有内容。
     *
     * 这个方法规则不写 `H`/`S`/`P` 标志的话，`assemble` 会直接失败
     * （AGP 解析时报 "At least one of flags ... must be specified"）——
     * 所以这里同时校验格式，避免有人手改后构建挂了才反应过来。
     */
    @Test
    fun `Baseline Profile 存在且格式合法`() {
        val prof = File("src/main/baseline-prof.txt")
            .takeIf { it.exists() }
            ?: File("app/src/main/baseline-prof.txt")
        assertTrue("baseline-prof.txt 应存在于 app/src/main/", prof.exists())

        val lines = prof.readLines(Charsets.UTF_8)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        assertTrue("baseline-prof.txt 不应为空", lines.isNotEmpty())

        // 方法规则必须带 flag；类规则是纯 `L...;`
        val methodRule = Regex("""^[HSP]+L[^;]+;->.+""")
        val classRule = Regex("""^L[^;]+;$""")
        lines.forEach { line ->
            assertTrue(
                "无法识别的 profile 行（会拖垮构建）：$line",
                methodRule.matches(line) || classRule.matches(line),
            )
        }
        assertTrue(
            "应至少包含一条我们自己的类的条目",
            lines.any { it.contains("cn/edu/qau") },
        )
    }

    /**
     * 提醒/静音的两个调度器都必须挂上续排（ChainReceiver）。
     *
     * 少了它，7 天窗口跑完就再也没人重排 —— 用户一周不开 App 就彻底收不到提醒，
     * 而且界面上毫无提示。这是个曾经真实发生过的 bug。
     */
    @Test
    fun `两个调度器都挂了续排闹钟`() {
        val reminder = src("src/main/java/cn/edu/qau/timetable/notify/ReminderScheduler.kt")
        val silence = src("src/main/java/cn/edu/qau/timetable/notify/SilenceScheduler.kt")

        assertTrue("ReminderScheduler 应调用 ChainReceiver.schedule",
            reminder.contains("ChainReceiver.schedule"))
        assertTrue("SilenceScheduler 应调用 ChainReceiver.schedule",
            silence.contains("ChainReceiver.schedule"))
    }

    /** 静音的有权限/无权限两种早退要分开报，否则用户不知道要去授权。 */
    @Test
    fun `静音排程把无权限单独报出来`() {
        val silence = src("src/main/java/cn/edu/qau/timetable/notify/SilenceScheduler.kt")
        assertTrue(
            "无「通知策略访问权限」时应写入 ScheduleStatus（重装后该权限会被撤销）",
            silence.contains("通知策略访问权限"),
        )
    }
}
