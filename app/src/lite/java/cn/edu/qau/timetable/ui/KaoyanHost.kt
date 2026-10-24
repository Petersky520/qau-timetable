package cn.edu.qau.timetable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 考研功能的接缝（Lite 版实现）。
 *
 * Lite 的取舍很简单：**不含考研模块**。
 *
 * ## 为什么不是「把 Tab 删掉」
 *
 * 删掉当然更干净，但用户如果有期待（听人说过选校推荐、或者在完整版用过），
 * 找不到入口只会以为是 bug。保留 Tab、点进去明说「这个版本不含考研」，
 * 是把「产品选择」和「功能故障」区分开 —— 前者不用排查。
 *
 * ## 为什么这个文件能让 Lite 真的变小
 *
 * 本文件不引用 `KaoyanScreen`，而共用代码也只引用这里的 [KaoyanHost]。
 * 于是在 Lite 变体里，KaoyanScreen 整条依赖链（KaoyanViewModel、
 * RecommendEngine、SiteCatalog、KaoyanRepository、AI 客户端…）
 * 从入口处就不可达，R8 会把它们全部裁掉。
 *
 * 换句话说：**体积收益来自「没人引用」，不是来自「文件被 exclude」。**
 * 这也是为什么 [KaoyanFeature.hasKaoyan] 要和入口一起放在 flavor 里。
 */
object KaoyanFeature {
    /** Lite 版：没有考研模块。 */
    const val hasKaoyan: Boolean = false
}

/** Lite 版的考研页 = 一句说明。见 [KaoyanFeature] 的 KDoc。 */
@Composable
fun KaoyanHost(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    // 参数刻意保留，与完整版签名一致；这里用不到，但调用方不该为此分叉。
    @Suppress("UNUSED_PARAMETER")
    val unused = vm to onOpenSettings

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("这个版本不含考研功能", style = MaterialTheme.typography.titleMedium)
            Text(
                "青小农 Lite 只保留课表相关功能：课表、今日、考试、成绩、调休、提醒、静音、桌面小组件。\n\n" +
                    "需要考研选校推荐、论文 / 资料站点、AI 搜索的话，请装完整版「青小农」。\n" +
                    "两个版本可以同时安装，互不影响。",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}
