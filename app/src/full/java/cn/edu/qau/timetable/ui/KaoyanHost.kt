package cn.edu.qau.timetable.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.edu.qau.timetable.ui.kaoyan.KaoyanScreen

/**
 * 考研功能的接缝（完整版实现）。
 *
 * ## 为什么需要这个文件
 *
 * 「考研」不是一个自成一体的模块，它横穿 7 个共用文件：MainActivity 的 Tab、
 * QauApp 的仓库注入、Daos 的表定义、Entities 的实体、SettingsStore 的字段、
 * MainViewModel 的构造参数、SettingsScreen 的设置项。
 *
 * 如果 Lite 版直接按目录排除考研源码，那 7 处引用会全部编译失败。
 * 而把共用文件复制两份的话，以后每个 bug 都要修两遍，两个版本会慢慢长歪。
 *
 * 所以这里留一个**单一接缝**：共用代码只认 [KaoyanFeature.hasKaoyan] 和
 * [KaoyanHost]，不再直接引用 `KaoyanScreen`。两个 flavor 各提供一份实现：
 *
 *   · full（本文件）：真的有考研页
 *   · lite：hasKaoyan = false，[KaoyanHost] 渲染一句说明
 *
 * 这样在 lite 里，`KaoyanScreen` 及其整条依赖链（KaoyanViewModel、
 * RecommendEngine、SiteCatalog、KaoyanRepository…）从入口处就不可达，
 * R8 会把它们连同 Compose 里用不到的部分一起裁掉 ——
 * 体积收益等同于删目录，但共用代码只有一份。
 */
object KaoyanFeature {
    /** Lite 版为 false。共用代码据此决定要不要显示考研相关的入口与设置项。 */
    const val hasKaoyan: Boolean = true
}

/**
 * 渲染考研页。
 *
 * 参数签名刻意与 `KaoyanScreen` 保持一致，切换实现时调用方不用改。
 */
@Composable
fun KaoyanHost(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    KaoyanScreen(vm = vm, modifier = modifier, onOpenSettings = onOpenSettings)
}
