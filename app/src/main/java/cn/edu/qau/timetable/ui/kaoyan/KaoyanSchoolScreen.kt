package cn.edu.qau.timetable.ui.kaoyan

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.edu.qau.timetable.BuildConfig
import cn.edu.qau.timetable.core.kaoyan.Ambition
import cn.edu.qau.timetable.core.kaoyan.KaoyanProfile
import cn.edu.qau.timetable.core.kaoyan.Major
import cn.edu.qau.timetable.core.kaoyan.MajorCategory
import cn.edu.qau.timetable.core.kaoyan.MajorSearch
import cn.edu.qau.timetable.core.kaoyan.Region
import cn.edu.qau.timetable.core.kaoyan.SchoolTier
import cn.edu.qau.timetable.ui.glass.LocalFloatingBarReserve
import cn.edu.qau.timetable.ui.motion.rememberEntranceWindow
import cn.edu.qau.timetable.ui.motion.staggeredAppear

/**
 * 空状态。
 *
 * 与 `ui/SimpleScreens.kt` 里那个 private 的同名 composable 是同一套写法，
 * 但这里**刻意再写一份**而不是把原来那个改成 public：那个函数的语义属于它自己的页面，
 * 一旦公开，别的模块会开始往里加参数，最后谁都不敢动它。
 */
@Composable
private fun EmptyState(title: String, hint: String, animate: Boolean = true) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                // animate 一路传下来：空状态在滚动/重组时被重新组合也不该重播。
                .staggeredAppear(0, animate = animate),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

/** 结果区的行：分组小标题，或一条院校。分组和条目共用一条列表，滚动位置才不会在组间跳。 */
// 类型定义搬到 KaoyanCardUi.kt（那里是 UI 层的 @Immutable 展示模型），
// 因为 RecommendRow.Item 现在装的是 KaoyanCardUi —— core 的 Recommendation 不能进 UI 模型，
// 而 UI 模型也不该被一个私有的 sealed interface 反向依赖。

/**
 * 选校推荐页。
 *
 * 结构是「一张很大的意向卡 + 分三组的推荐结果」，两者放在**同一个** LazyColumn 里
 * 而不是「卡片固定在上面、结果单独滚动」：意向卡有六七行 chip，固定住会吃掉半屏，
 * 手机上根本看不清结果。让它跟着滚走，是这个尺寸下唯一合理的选择。
 *
 * ## 性能约定（改这个文件前先读）
 *
 * 这页是用户报「掉帧」的那一页，这几条不是风格问题：
 *  - 列表数据一律 `remember(依赖)`，不在 composable 体里 buildList / filter；
 *  - 列表项必须有稳定 `key`，否则列表更新时 Compose 无法复用节点；
 *  - 进度条自绘（见 [ScoreBar]），不用 Material 的 `LinearProgressIndicator`；
 *  - 卡片拿的是 UI 层的 `@Immutable` 模型（[KaoyanCardUi]），不是 core 的 `Recommendation`。
 */
@Composable
fun KaoyanSchoolScreen(vm: KaoyanViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsState()
    // 卡片已经是 ViewModel 里投影好的 @Immutable 模型，composable 这边不做任何映射。
    val cards by vm.recommendCards.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val busy by vm.busy.collectAsState()

    // 按档分组。**必须 remember**：这段以前是内联在 composable 体里的 buildList，
    // 每次重组都跑 O(3N) 过滤 + 分配四份 List。滚动 / 收藏任一状态变化都会触发重组，
    // 于是那些分配每帧都在发生。
    //
    // 依赖只有 `cards`：ViewModel 的 Flow 只在「目录或画像真的变了」时才发新引用，
    // 所以同一份数据下重组多少次，这个 lambda 都只跑一次 —— 分组结果从头到尾是同一个对象。
    //
    // 里面的 perfLog 是**测量用的探针**，同时也是这个优化的验收口径：
    //   adb logcat -s KaoyanPerf
    // 进页面 → 打一行 "rows rebuilt"；滚动 / 收藏 / 切周次 → **一行都不再打**。
    // 这一行是"重建已消除"的直接证据（以前它会在每一次重组时打一次）。
    val rows = remember(cards) {
        val out = buildRecommendRows(cards)
        if (BuildConfig.DEBUG) {
            Log.d("KaoyanPerf", "rows rebuilt: ${cards.size} cards -> ${out.size} rows")
        }
        out
    }

    // 入场窗口：开页后几百毫秒内为 true，之后永久翻 false。
    // 它的 key 是 Unit，所以在页面存续期间**不会**因为滚动重新开窗：
    // LazyColumn 滚出视口销毁的项被滚回来时 `entrance` 已是 false，
    // staggeredAppear 会直接落位、不跑 Animatable（这就是 F 项的落地方式）。
    val entrance = rememberEntranceWindow(rows.size)

    Column(modifier.fillMaxSize()) {
        // ---------------------------------------------------------- 标题条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "共 ${cards.size} 条匹配结果",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (settings.kaoyanCatalogUrl.isBlank()) {
                        "内置样例目录。填了刷新地址可以从自己的 JSON 拉一份。"
                    } else {
                        "目录来源：${settings.kaoyanCatalogUrl}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(
                onClick = { vm.refreshCatalog(settings.kaoyanCatalogUrl) },
                enabled = !busy,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新目录")
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 8.dp,
                end = 12.dp,
                // 悬浮底栏会盖住内容，这里必须让出那一段，否则最后一条永远滚不出来
                bottom = 12.dp + LocalFloatingBarReserve.current,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "profile") {
                ProfileCard(
                    vm = vm,
                    modifier = Modifier.staggeredAppear(0, animate = entrance),
                )
            }

            if (cards.isEmpty()) {
                // 空状态塞进列表里，而不是整页替换：意向卡还得能被改，
                // 否则用户看到「没有结果」之后连调整条件的地方都没有了。
                // 给一个固定高度，是因为 EmptyState 内部用 fillMaxSize 居中 ——
                // 直接放进 LazyColumn 会拿到无界高度、量出来是 0。
                item(key = "empty") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .staggeredAppear(1, animate = entrance),
                    ) {
                        EmptyState(
                            "还没有可推荐的院校",
                            "目录里没有数据，或者条件太窄。放宽地区、把「最低招生人数」调小试试；" +
                                "内置目录没灌进来时，也可以在设置页填一个刷新地址。",
                            animate = entrance,
                        )
                    }
                }
            }

            // key 是**必须**的，不是可选的优化：
            // 以前名单没有 key，Compose 只能按位置复用 —— 用户改一下意向、结果顺序变了，
            // 每个位置上的内容都换了一个，于是整屏 item 全部重建（连图片/文本测量一起）。
            // 有了稳定 key，卡片跟着自己的院校 id 走，顺序变化只是移动节点。
            itemsIndexed(
                items = rows,
                key = { _, row ->
                    when (row) {
                        is RecommendRow.Header -> "h-${row.tier.name}"
                        is RecommendRow.Item -> "i-${row.card.id}"
                    }
                },
                contentType = { _, row ->
                    // 两种行的布局完全不同，分开 contentType 后 Compose 不会拿
                    // 复用池里的 header 槽去装卡片。
                    when (row) {
                        is RecommendRow.Header -> "header"
                        is RecommendRow.Item -> "card"
                    }
                },
            ) { index, row ->
                // 序号 +1：意向卡已经占了 0 号，结果从下一拍接上
                when (row) {
                    is RecommendRow.Header -> GroupHeader(
                        row = row,
                        modifier = Modifier.staggeredAppear(index + 1, animate = entrance),
                    )

                    is RecommendRow.Item -> RecommendCard(
                        card = row.card,
                        favorite = row.card.id in favorites,
                        // 收藏回调只捕获 id，不捕获整张卡 —— 否则每次收藏都让
                        // 这张卡的 lambda 换一个实例，等于白送一次重组。
                        onToggleFavorite = {
                            vm.toggleFavorite(row.card.id)
                        },
                        modifier = Modifier.staggeredAppear(index + 1, animate = entrance),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 意向卡
// ---------------------------------------------------------------------------

/**
 * 报考意向。
 *
 * 每一项都做成 chip / switch 而不是输入框：这张卡的目的是「一分钟内调出想要的结果」，
 * 打字会让整个过程变慢；唯一需要输入的是最低招生人数，因为那是任意整数。
 */
@Composable
private fun ProfileCard(vm: KaoyanViewModel, modifier: Modifier = Modifier) {
    val profile by vm.profile.collectAsState()

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("报考意向", style = MaterialTheme.typography.titleSmall)
            Text(
                "调整下面的条件，结果会立刻重排 —— 不用点「开始推荐」。",
                style = MaterialTheme.typography.labelSmall,
            )

            ChipLabel("专业")
            // 搜索框挂在「门类 → 专业」两级结构**之上**（见 [MajorPicker]）：
            // 没关键词时完全走 MajorSelector 的原路径，有关键词时只换它的
            // `majors` / `grouped` 两个参数，不动它内部实现。
            MajorPicker(
                profile = profile,
                onSelect = { vm.setMajor(it) },
            )
            Text(
                if (profile.major.mathRequired) {
                    "该专业通常要求考数学（${profile.major.code}）。"
                } else {
                    "该专业通常不考数学（${profile.major.code}）。"
                },
                style = MaterialTheme.typography.labelSmall,
            )

            ChipLabel("意向地区")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Region.entries.forEach { r ->
                    FilterChip(
                        selected = profile.region == r,
                        onClick = { vm.setRegion(r) },
                        label = { Text(r.label) },
                    )
                }
            }

            ChipLabel("报考意愿")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Ambition.entries.forEach { a ->
                    FilterChip(
                        selected = profile.ambition == a,
                        onClick = { vm.setAmbition(a) },
                        label = { Text(a.label) },
                    )
                }
            }
            Text(profile.ambition.hint, style = MaterialTheme.typography.labelSmall)

            ChipLabel("本科层次")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SchoolTier.entries.forEach { t ->
                    FilterChip(
                        selected = profile.undergraduateTier == t,
                        onClick = { vm.setUndergraduateTier(t) },
                        label = { Text(t.label) },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 2.dp))

            SwitchRow(
                title = "接受跨专业",
                subtitle = if (profile.crossMajor) {
                    "专业不对口的院校只扣很少分，会一起列出来。"
                } else {
                    "专业不对口的院校会明显扣分，避免给你一堆用不上的结果。"
                },
                checked = profile.crossMajor,
                onCheckedChange = { vm.setCrossMajor(it) },
            )

            SwitchRow(
                title = "接受考数学",
                subtitle = "关掉之后，要求考数学的院校专业会被扣分（不会直接消失）。",
                checked = profile.acceptMath,
                onCheckedChange = { vm.setAcceptMath(it) },
            )

            OutlinedTextField(
                value = if (profile.minQuota == 0) "" else profile.minQuota.toString(),
                onValueChange = { raw -> vm.setMinQuota(raw.filter { it.isDigit() }.toIntOrNull() ?: 0) },
                label = { Text("最低招生人数") },
                placeholder = { Text("留空 = 不限") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ChipLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
}

/**
 * 专业选择区的入口：搜索框 + 两级门类结构。
 *
 * ## 结构
 *
 * ```
 * [ 搜索框（始终可见） ]
 *   ├─ 无关键词 → MajorSelector(grouped = true)   门类 chip 行 + 该门类下的专业 chip 行
 *   └─ 有关键词 → MajorSelector(grouped = false)  门类行让位，平铺搜索结果
 * ```
 *
 * ## 为什么在这外面再包一层，而不是改 [MajorSelector]
 *
 * [MajorSelector] 的 `majors` / `grouped` 两个参数就是为这件事留的接口。
 * 在这里包一层，搜索只影响"传什么进去"，`MajorSelector` 内部一行都不用动 ——
 * 也就不会在以后改门类逻辑时把搜索功能一起弄坏。
 *
 * ## 平铺模式依赖的隐式契约（改 [MajorSelector] 前必读）
 *
 * `grouped = false` 时 [MajorSelector] 内部的 `groups` 为空 list，
 * 于是「第一级门类 chip 行」整段被 `if (groups.isNotEmpty())` 跳过，
 * 第二级直接遍历传入的 `majors` —— **这就是平铺**。
 * 这个契约是把 `groups` 当开关用的，没有第二个标志位；
 * 如果有人给 `MajorSelector` 加"始终显示门类行"的逻辑，搜索态会立刻退回两级。
 */
@Composable
private fun MajorPicker(
    profile: KaoyanProfile,
    onSelect: (Major) -> Unit,
    modifier: Modifier = Modifier,
) {
    // rememberSaveable：用户切到论文页再切回来，搜索词还在。
    // 用 remember 的话一次 tab 切换就清空了 —— 用户会以为是自己没输进去。
    var keyword by rememberSaveable { mutableStateOf("") }

    // 只依赖 keyword：`Major.entries` 是稳定引用，不用进 key。
    // 这一句就是"输入时只过滤、不重组整页"的落点 —— 同一个 keyword 下
    // 无论外层重组多少次，过滤都只跑一次，返回同一个 List 实例，
    // 下游的 MajorSelector 也就能因引用相等而跳过重组。
    val filtered = remember(keyword) {
        MajorSearch.search(keyword, Major.entries)
    }
    // 「有没有在搜」和「搜了没结果」是两件事，用同一套判断（MajorSearch.isBlank），
    // 免得一边 isEmpty 一边 isBlank，用户敲一个空格就掉进空结果分支。
    val searching = !MajorSearch.isBlank(keyword)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            placeholder = {
                Text(
                    "搜专业名、代码或门类，如「临床」「081」「医学」",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = {
                // 只在有内容时才给清空按钮：空框上挂一个 X 是纯粹的视觉噪音，
                // 而且会让人误以为"框里有东西"。
                if (keyword.isNotEmpty()) {
                    IconButton(onClick = { keyword = "" }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "清空搜索",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
        )

        when {
            // 没在搜：原样走两级结构，行为和加搜索之前完全一致。
            !searching -> MajorSelector(
                profile = profile,
                onSelect = onSelect,
            )

            // 搜到了：门类行让位，平铺结果。
            filtered.isNotEmpty() -> {
                Text(
                    "找到 ${filtered.size} 个专业",
                    style = MaterialTheme.typography.labelSmall,
                )
                MajorSelector(
                    profile = profile,
                    onSelect = onSelect,
                    majors = filtered,
                    grouped = false,
                )
            }

            // 搜了但没结果：给建议，不要让区域塌成空白。
            // 空白会让人以为"搜索框坏了"，一行字至少说明它工作正常、只是没命中。
            else -> Text(
                "没有匹配的专业。试试更短的关键词，或直接按门类浏览。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 专业选择区（两级：门类 → 该门类下的专业）。
 *
 * ## 为什么单独抽出来
 *
 * 1. **缩小重组范围**：专业现在有 142 个。如果它们和「地区 / 意愿 / 本科层次 / 开关」
 *    挤在同一个 composer 里，改任意一项都会把这一整片 chip 全部重新组合。
 *    抽出来之后，它的输入只有 [profile] / [onSelect] / [majors] / [grouped]，
 *    别的条件变了它不参与重组。
 * 2. **可被整体替换**：专业搜索要在这之上加关键词框。有关键词时门类那一级要隐藏、
 *    直接平铺搜索结果 —— 那只需要换个传参（`majors = 过滤结果, grouped = false`），
 *    不用动这里的内部实现。
 * 3. **不能平铺**：142 个 chip 一次性铺出来，光是文本测量就够卡了（这也是性能任务
 *    要解决的东西）。两级之后同一时刻只有「14 个门类 + 该门类下的十几个专业」。
 *
 * @param profile 当前画像。只用到其中的 `major`，但直接收整个对象是为了让
 *   签名在后续加字段时不用改调用方。
 * @param onSelect 选中某个专业。
 * @param majors 要展示的专业集合。默认全部；搜索时传过滤后的结果。
 * @param grouped 是否展示门类那一级。搜索时传 false → 平铺。
 */
@Composable
private fun MajorSelector(
    profile: KaoyanProfile,
    onSelect: (Major) -> Unit,
    modifier: Modifier = Modifier,
    majors: List<Major> = Major.entries,
    grouped: Boolean = true,
) {
    // 门类分组。依赖只有 majors：同一个 majors 引用不会重算分组。
    val groups = remember(majors, grouped) {
        if (grouped) majorGroups(majors) else emptyList()
    }

    // 当前选中的门类。
    //
    // 直接由 profile.major 推出来，而不是自己存一份 mutableState：
    // 存一份的话，用户切门类但没选专业时，UI 上的"高亮门类"和实际用于推荐的
    // profile.major 就会不一致 —— 两个真源迟早打架。
    // 用户的每一下点击都会立刻选中该门类下的第一个专业，所以高亮永远跟着真值走。
    val activeCategory = remember(groups, profile.major) {
        groups.firstOrNull { (_, list) -> list.any { it == profile.major } }?.first
            ?: groups.firstOrNull()?.first
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (groups.isNotEmpty()) {
            // 第一级：门类。横向滚动，一行放下，不换行堆成墙。
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                groups.forEach { (category, list) ->
                    FilterChip(
                        selected = category == activeCategory,
                        onClick = {
                            // 点门类 = 跳到该门类的第一个专业。
                            // 不做「只筛选不选中」：专业是推荐引擎的必需输入，
                            // 留一个"什么都没选"的中间态会让下面的结果区无从解释。
                            list.firstOrNull()?.let(onSelect)
                        },
                        label = { Text(category.label) },
                    )
                }
            }
        }

        // 第二级：当前门类下的专业（搜索时是平铺的过滤结果）。
        val visible = remember(groups, profile.major) {
            groups.firstOrNull { (category, list) ->
                category == activeCategory && list.any { it == profile.major }
            }?.second
                ?: groups.firstOrNull()?.second
                ?: majors
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            visible.forEach { m ->
                FilterChip(
                    selected = profile.major == m,
                    onClick = { onSelect(m) },
                    label = { Text(m.label) },
                )
            }
        }
    }
}

/**
 * 把专业按学科门类分组。
 *
 * 组序取 [MajorCategory.entries] 的声明顺序（= 教育部学科目录的门类序），
 * **不是** map 的插入顺序：`groupBy` 的 key 顺序取决于专业枚举的声明顺序，
 * 如果照抄，用户会在两次升级之间发现门类的排列变了、找不到原来的位置。
 * 门类下没有任何专业时整组不出现（空的 chip 行占位只会浪费高度）。
 */
private fun majorGroups(majors: List<Major>): List<Pair<MajorCategory, List<Major>>> {
    if (majors.isEmpty()) return emptyList()
    val byCategory = majors.groupBy { it.category }
    return MajorCategory.entries.mapNotNull { c ->
        byCategory[c]?.takeIf { it.isNotEmpty() }?.let { c to it }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ---------------------------------------------------------------------------
// 结果
// ---------------------------------------------------------------------------

@Composable
private fun GroupHeader(row: RecommendRow.Header, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
    ) {
        Text(
            "${row.tier.label} · ${row.count} 所",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(row.tier.hint, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * 一条推荐结果。
 *
 * 卡片里**同时**列出理由和警告，而且警告用错误色：推荐结果只报喜就等于广告，
 * 用户需要知道「这所为什么被扣分」才能判断要不要真的去冲。
 *
 * 参数是 [KaoyanCardUi]（`@Immutable`）而不是 `Recommendation`：
 * 后者在 core 层、不能标 Compose 注解，会让这张卡每次父级重组都跟着重组。
 */
@Composable
private fun RecommendCard(
    card: KaoyanCardUi,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.schoolName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                // 层次只是个标签，不该长得像能点的过滤器 —— 用 Surface 画，
                // 而不是 FilterChip（chip 点下去没有任何反应，是明确的假交互）。
                TierBadge(card.tierBadge)
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (favorite) "取消收藏" else "收藏",
                        tint = if (favorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Text(card.locationLine, style = MaterialTheme.typography.bodySmall)
            Text(card.majorLine, style = MaterialTheme.typography.bodySmall)
            Text(card.metricsLine, style = MaterialTheme.typography.labelSmall)

            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreBar(
                    fraction = card.scoreFraction,
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                )
                Text(
                    "${card.score}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            card.reasons.forEach { reason ->
                Text(
                    "+ $reason",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            card.warnings.forEach { warning ->
                Text(
                    "! $warning",
                    style = MaterialTheme.typography.labelSmall,
                    // 警告必须显眼：只说好话的推荐是骗人的
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (card.tagsLine.isNotEmpty()) {
                Text(card.tagsLine, style = MaterialTheme.typography.labelSmall)
            }
            if (card.noteLine.isNotEmpty()) {
                Text(card.noteLine, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * 轻量进度条：一条 4dp 高的圆角矩形，按分数比例填 —— 零动画、零重组、零语义开销。
 *
 * ## 为什么不用 Material3 的 `LinearProgressIndicator`
 *
 * 1. 它自带**不确定态动画**的规范（`IndeterminateAnimationSpec`），哪怕是 determinate
 *    用法，组件内部也挂着一条无限循环的 `rememberInfiniteTransition` 语义和一大堆
 *    draw 逻辑；滚动时这些每帧都在被重新求值。
 * 2. `progress = { score / 100f }` 这个 **lambda 版本**会在绘制阶段读状态，
 *    等于把"读状态"从组合阶段挪到绘制阶段 —— 看着更省，实际上让**每一帧**都变成
 *    可能失效的读取点，滚动时持续触发重绘。
 * 3. 它还带 `ProgressBarRangeInfo` 语义节点，60 张卡就是 60 个无障碍节点白建。
 *
 * 这里就是两个 `Box`：底轨 + 填充。填充比例是**普通 Float 参数**（在 ViewModel 的
 * 投影里算好），不是 lambda，也不读快照状态 —— 除非分数变了，否则它连重组都不需要。
 *
 * 4dp 高度配 `CircleShape` 就是一条胶囊，和 M3 的 `LinearProgressIndicator` 观感一致，
 * 但绘制代价是"两次 drawRect + 一次 clip"。
 */
@Composable
private fun ScoreBar(fraction: Float, modifier: Modifier = Modifier) {
    // 颜色只在主题变化时重读一次，而不是每次重组都从 MaterialTheme 里翻。
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val barColor = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .height(4.dp)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(barColor),
        )
    }
}

/**
 * 院校层次小标签。
 *
 * 用 `Surface` 画而不是 `FilterChip`：chip 天生带「可点」的暗示，
 * 这颗标签点了没有任何反应 —— 那是最招人烦的一种假交互。
 */
@Composable
private fun TierBadge(tier: String) {
    if (tier.isBlank()) return
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            tier,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
