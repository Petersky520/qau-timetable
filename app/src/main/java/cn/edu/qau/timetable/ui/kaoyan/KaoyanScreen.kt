package cn.edu.qau.timetable.ui.kaoyan

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cn.edu.qau.timetable.QauApp
import cn.edu.qau.timetable.core.kaoyan.SiteCategory
import cn.edu.qau.timetable.ui.MainViewModel

/**
 * 考研模块的二级导航。
 *
 * 只有四个入口，用 chip 行而不是再套一层 BottomBar —— 底栏已经是全局导航，
 * 再叠一条等于两级底栏，手势区会打架。
 */
private enum class KaoyanTab(val label: String) {
    SCHOOL("选校"),
    THESIS("论文"),
    STUDY("资料"),
    AI("AI 搜索"),
}

/** 论文页显示的分类：检索库 + 专利标准。 */
private val THESIS_CATEGORIES: Set<SiteCategory> =
    setOf(SiteCategory.THESIS, SiteCategory.PATENT)

/** 资料页显示的分类：学习资料 + 公开课 + 数据政策。 */
private val STUDY_CATEGORIES: Set<SiteCategory> =
    setOf(SiteCategory.STUDY, SiteCategory.COURSE, SiteCategory.DATA)

/**
 * 考研工具主入口。
 *
 * ## ViewModel 为什么在这里创建而不是在 MainActivity
 * [KaoyanViewModel] 只服务这一个模块，四个子页共享一份实例就够了。
 * 放在这里之后，MainActivity 只需要认识 [KaoyanScreen] 一个名字，
 * 也就不会为了一个标签页去动全局的依赖装配。
 *
 * ## 为什么还要收一个 [vm]
 * 这个 App 的 Snackbar 宿主挂在 `AppRoot` 上，只订阅 `MainViewModel.toast`。
 * 考研模块自己的提示要能让用户看见，就得借这条已有的通道 ——
 * 下面那个 LaunchedEffect 把 [KaoyanViewModel.toast] 转过去，
 * 于是「地址没填」这种提示和「学期已更新」长得一模一样。
 *
 * @param onOpenSettings 打开全局设置覆盖层。设置页由 `AppRoot` 控制，
 *   这个模块自己打不开它，所以由调用方把「怎么去设置」注入进来。
 *   默认值只为让 `KaoyanScreen(vm, Modifier)` 这种两参调用照样成立；
 *   拿不到真实入口时退化成一句路径提示，也比一个点了没反应的按钮强。
 */
@Composable
fun KaoyanScreen(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as QauApp).container
    val kaoyanVm: KaoyanViewModel = viewModel(
        factory = viewModelFactory {
            initializer { KaoyanViewModel(container.kaoyan) }
        },
    )

    var tab by rememberSaveable { mutableStateOf(KaoyanTab.SCHOOL) }

    // 把模块内的提示转进全局 Snackbar 通道（见上面的 KDoc）
    val toast by kaoyanVm.toast.collectAsState()
    LaunchedEffect(toast) {
        toast?.let {
            vm.toast(it)
            kaoyanVm.clearToast()
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KaoyanTab.entries.forEach { t ->
                FilterChip(
                    selected = tab == t,
                    onClick = { tab = t },
                    label = { Text(t.label) },
                )
            }
        }

        when (tab) {
            KaoyanTab.SCHOOL -> KaoyanSchoolScreen(kaoyanVm, Modifier.fillMaxSize())

            KaoyanTab.THESIS -> KaoyanSiteScreen(
                vm = kaoyanVm,
                categories = THESIS_CATEGORIES,
                modifier = Modifier.fillMaxSize(),
            )

            KaoyanTab.STUDY -> KaoyanSiteScreen(
                vm = kaoyanVm,
                categories = STUDY_CATEGORIES,
                modifier = Modifier.fillMaxSize(),
            )

            KaoyanTab.AI -> KaoyanAiScreen(
                vm = kaoyanVm,
                onGoSettings = {
                    // 有真实入口就跳过去；没有（KaoyanScreen 被单独预览/复用时）
                    // 就把路径说清楚，而不是留一个点了没反应的按钮。
                    val open = onOpenSettings
                    if (open != null) {
                        open()
                    } else {
                        vm.toast("点右上角齿轮，打开「AI 搜索」卡片：填 base URL、密钥、模型名")
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
