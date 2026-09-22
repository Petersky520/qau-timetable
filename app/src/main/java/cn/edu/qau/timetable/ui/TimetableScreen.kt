package cn.edu.qau.timetable.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.PeriodTimes
import cn.edu.qau.timetable.domain.CourseEvent
import cn.edu.qau.timetable.ui.theme.courseColorFor

private val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
private val CELL_HEIGHT = 62.dp
// 左列除了节号，还要竖排放下 "08:00" / "08:45" 两行时间，
    // 30dp 会把时间挤没，放宽到 38dp（7 个日列各让出约 1dp，可忽略）。
private val LABEL_WIDTH = 38.dp

@Composable
fun TimetableScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val courses by vm.courses.collectAsState()
    val term by vm.activeTerm.collectAsState()
    val weekOverride by vm.weekOverride.collectAsState()
    val settings by vm.settings.collectAsState()

    val realCurrent = vm.currentWeek()
    val week = if (weekOverride > 0) weekOverride else realCurrent.coerceAtLeast(1)
    val campus = term?.toDomain()?.campus ?: settings.campus

    Column(modifier.fillMaxSize()) {
        WeekBar(
            week = week,
            isCurrent = weekOverride == 0 || week == realCurrent,
            campus = campus,
            hasTerm = term != null,
            onPrev = { vm.stepWeek(-1) },
            onNext = { vm.stepWeek(1) },
            onToday = { vm.setWeek(0) },
        )

        if (term == null || courses.isEmpty()) {
            EmptyTimetable()
        } else {
            TimetableGrid(courses = courses, week = week, campus = campus)
        }
    }
}

@Composable
private fun WeekBar(
    week: Int,
    isCurrent: Boolean,
    campus: Campus,
    hasTerm: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上一周")
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (hasTerm) "第 $week 周" else "未设置学期",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (isCurrent) "本周 · ${campus.label}" else campus.label,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "下一周")
            }
            TextButton(onClick = onToday) { Text("本周") }
        }
    }
}

@Composable
private fun EmptyTimetable() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(24.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("还没有课表数据", style = MaterialTheme.typography.titleMedium)
                Text(
                    "点右上角的「同步」按钮，在 App 内登录青农大教务系统，" +
                        "登录成功后会自动抓取本学期课表。\n\n" +
                        "整个过程 App 不会保存你的密码。",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun TimetableGrid(courses: List<CourseEvent>, week: Int, campus: Campus) {
    val visible = remember(courses, week) { courses.filter { it.occursIn(week) } }

    // 渲染行数取「已知节次数」与「数据里出现的最大节次」的较大者 ——
    // 这样即使学校有作息表之外的节次，也不会把课画到格子外面。
    val periodCount = remember(visible) {
        maxOf(
            PeriodTimes.PERIOD_COUNT,
            visible.maxOfOrNull { it.endPeriod } ?: 0,
            visible.maxOfOrNull { it.startPeriod } ?: 0,
        )
    }

    val byStart = remember(visible) { visible.groupBy { it.dayOfWeek to it.startPeriod } }
    val covered = remember(visible) {
        val set = HashSet<Pair<Int, Int>>()
        visible.forEach { c ->
            for (p in (c.startPeriod + 1)..c.endPeriod) set.add(c.dayOfWeek to p)
        }
        set
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // 表头
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(LABEL_WIDTH))
            for (label in DAY_LABELS) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // 表体：按「列」排布，而不是按「行」。
        //
        // 之前是每行一个固定高度的 Row，跨节的课靠 requiredHeight 让盒子
        // 溢出父容器来撑高度 —— 这条路径在真机上出过问题（有机化学实验
        // 1-4 节那格位置对、跨度对，但文字不见了）。
        //
        // 现在每天单独一列，跨度直接由**盒子自身高度**表达：
        // 每列总高严格等于 periodCount * CELL_HEIGHT，跨行天然对齐，
        // 不存在任何溢出，也就没有"被后面的行盖住/裁掉"的可能。
        Row(Modifier.fillMaxWidth()) {
            // 节次列：节号 + 该节的「上课 / 下课」时间。
            // 作息表按校区不同（城阳 vs 平度上午整体差 30 分钟），
            // 所以时间必须由 campus 决定，不能写死。
            val periods = remember(campus) { PeriodTimes.of(campus) }
            Column(Modifier.width(LABEL_WIDTH)) {
                for (period in 1..periodCount) {
                    val slot = periods.firstOrNull { it.index == period }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(CELL_HEIGHT),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // 必须显式给 lineHeight：否则会继承 bodyLarge 的 24sp 行高，
                            // 三行加起来 72dp 直接撑爆 62dp 的格子。
                            Text(
                                text = "$period",
                                fontSize = 11.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                            // 学校节次比作息表多的兜底：没有时间就只显示节号
                            if (slot != null) {
                                Text(
                                    text = slot.startText,
                                    fontSize = 8.sp,
                                    lineHeight = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                Text(
                                    text = slot.endText,
                                    fontSize = 8.sp,
                                    lineHeight = 10.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
            // 周一..周日，各一列
            for (day in 1..7) {
                Column(Modifier.weight(1f)) {
                    var period = 1
                    while (period <= periodCount) {
                        val starts = byStart[day to period]
                        if (starts != null && starts.isNotEmpty()) {
                            val main = starts.first()
                            // 不设上限：4 节的实验课也要能完整撑开
                            val span = (main.endPeriod - main.startPeriod + 1)
                                .coerceIn(1, periodCount)
                            CourseCell(
                                course = main,
                                extra = starts.size - 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(CELL_HEIGHT * span)
                                    .padding(1.dp),
                            )
                            period += span
                        } else if (covered.contains(day to period)) {
                            // 理论上到不了这里（上面已经按 span 跳过），留作兜底
                            period++
                        } else {
                            Spacer(Modifier.fillMaxWidth().height(CELL_HEIGHT))
                            period++
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CourseCell(course: CourseEvent, extra: Int, modifier: Modifier) {
    val color = courseColorFor(course.name)
    Surface(
        modifier = modifier.fillMaxWidth(),
        // 用主题里的 Expressive 形状刻度，而不是写死圆角
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(horizontal = 3.dp, vertical = 3.dp)) {
            // 显式指定颜色：Surface 的 contentColor 对自定义色会退化成
            // Color.Unspecified（其 alpha 为 NaN），有让文字不可见的风险。
            // 名字为空时显示占位符，避免出现"看上去是空框"而无从判断。
            Text(
                text = course.name.ifBlank { "(未命名)" },
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (extra > 0) {
                Text(
                    text = "+$extra",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            if (course.room.isNotEmpty()) {
                Text(
                    text = course.room,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (course.teacher.isNotEmpty()) {
                Text(
                    text = course.teacher,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
