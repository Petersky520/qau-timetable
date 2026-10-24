package cn.edu.qau.timetable.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 考研功能的接缝（Lite 版实现）。
 *
 * Lite **不提供**考研功能：底栏里没有「考研」这一项
 * （见 `Tab.KAOYAN` 的 `inLite = false`），用户点不到这里。
 *
 * ## 那为什么还要留着这个函数
 *
 * `TabHost` 的 `when` 需要对 `Tab.KAOYAN` 有一个分支，否则 Kotlin 的
 * 穷尽性检查不过。所以保留一个空实现把它覆盖掉 ——
 * **这个分支在 Lite 里永远不会被走到**（Tab 列表里已经没有它）。
 *
 * ⚠️ 不要在这里加「请下载完整版」之类的引导：Lite 的定位就是不含考研，
 * 给用户留一个「点得到但用不了、还劝你装别的 App」的入口，
 * 比干脆没有这一项更让人困惑。
 *
 * ## 体积
 *
 * 本文件不引用 `KaoyanScreen`，而共用代码也只引用这里的 [KaoyanHost]。
 * 于是 Lite 变体里 KaoyanScreen 整条依赖链（KaoyanViewModel、RecommendEngine、
 * SiteCatalog、KaoyanRepository、AI 客户端…）从入口处就不可达，
 * R8 会把它们裁掉 —— 体积收益来自「没人引用」，不是来自「文件被 exclude」。
 */
object KaoyanFeature {
    /** Lite 版：没有考研模块。 */
    const val hasKaoyan: Boolean = false
}

/**
 * Lite 版的考研占位：空白。
 *
 * 不可达（见 [KaoyanFeature] 的 KDoc），只为满足 `when` 的穷尽性。
 */
@Composable
fun KaoyanHost(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    // 参数与完整版保持一致，避免调用方分叉。
    @Suppress("UNUSED_PARAMETER")
    val unused = listOf(vm, onOpenSettings)
}
