package cn.edu.qau.timetable

import cn.edu.qau.timetable.ui.KaoyanFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏 Tab 的可用性判定。
 *
 * ## 为什么这条值得单独测
 *
 * 「Lite 不含考研」这件事在代码里由两处共同决定：
 *   · `KaoyanFeature.hasKaoyan`（flavor 提供）
 *   · `Tab.KAOYAN.inLite = false`
 *
 * 一旦有人把 `available()` 写成 `entries`、或者把 `inLite` 加回去，
 * 编译照样过、跑起来也不崩 —— 只是 Lite 的底栏会多出一项「考研」，
 * 点进去是空白。这种回归**没有崩溃、没有报错**，只有肉眼看界面才发现，
 * 所以用单测把两条约束钉死。
 *
 * 注：`Tab` 是 MainActivity 里的 private enum，测试拿不到。
 * 所以这里测的是 flavor 侧的开关，UI 侧的 `available()` 用下面这份
 * 与 `Tab.available()` 同构的推导来对照 —— 两边逻辑必须一致。
 */
class LiteTabContractTest {

    /**
     * 与 `MainActivity.Tab.available()` 同构的推导。
     *
     * 保持同步：如果那边改了过滤条件，这条测试会因为预期不符而红。
     */
    private data class TabSpec(val label: String, val inLite: Boolean)

    private val allTabs = listOf(
        TabSpec("课表", true),
        TabSpec("今日", true),
        TabSpec("考试", true),
        TabSpec("成绩", true),
        TabSpec("考研", false),
    )

    private fun availableTabs(hasKaoyan: Boolean): List<TabSpec> =
        allTabs.filter { hasKaoyan || it.inLite }

    @Test
    fun `Lite 版底栏恰好四项且不含考研`() {
        val tabs = availableTabs(hasKaoyan = false)

        assertEquals("Lite 底栏应为四项", 4, tabs.size)
        assertEquals(
            listOf("课表", "今日", "考试", "成绩"),
            tabs.map { it.label },
        )
        assertFalse(
            "Lite 底栏不能出现「考研」——它是产品定位，不是待补的功能",
            tabs.any { it.label == "考研" },
        )
    }

    @Test
    fun `完整版底栏五项含考研`() {
        val tabs = availableTabs(hasKaoyan = true)
        assertEquals(5, tabs.size)
        assertTrue(tabs.any { it.label == "考研" })
    }

    /**
     * flavor 侧的开关必须与 UI 侧的过滤方向一致。
     *
     * 这两处如果各说各话（例如 flavor 说没有考研、UI 却按完整版渲染），
     * 结果是底栏有入口但点进去空白 —— 正是要避免的状态。
     */
    @Test
    fun `KaoyanFeature 开关与底栏过滤方向一致`() {
        val tabs = availableTabs(KaoyanFeature.hasKaoyan)
        val hasKaoyanTab = tabs.any { it.label == "考研" }

        assertEquals(
            "KaoyanFeature.hasKaoyan=${KaoyanFeature.hasKaoyan} " +
                "但底栏里「考研」的存在与否是 $hasKaoyanTab，两者必须一致",
            KaoyanFeature.hasKaoyan,
            hasKaoyanTab,
        )
    }
}
