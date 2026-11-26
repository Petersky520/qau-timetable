package cn.edu.qau.timetable.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import cn.edu.qau.timetable.ui.theme.courseColorFor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.DayOverrides
import cn.edu.qau.timetable.core.GradeRecord
import cn.edu.qau.timetable.core.GradeStats
import cn.edu.qau.timetable.core.GradeSummary
import cn.edu.qau.timetable.core.PeriodTimes
import cn.edu.qau.timetable.data.model.GradeEntity
import cn.edu.qau.timetable.domain.CourseEvent
import cn.edu.qau.timetable.domain.coursesOn
import cn.edu.qau.timetable.domain.nextCourseDay
import cn.edu.qau.timetable.ui.motion.rememberEntranceWindow
import cn.edu.qau.timetable.ui.motion.staggeredAppear
import java.time.LocalDate

private val WEEKDAY_CN = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@Composable
internal fun rememberLocalDate(): LocalDate {
    val today by produceState(LocalDate.now()) {
        while (true) {
            value = LocalDate.now()
            delay(30_000)
        }
    }
    return today
}

@Composable
fun TodayScreen(vm: MainViewModel, modifier: Modifier = Modifier, onSync: (() -> Unit)? = null) {
    val courses by vm.courses.collectAsState()
    val entity by vm.activeTerm.collectAsState()
    val settings by vm.settings.collectAsState()
    val loaded by vm.timetableLoaded.collectAsState()
    val today = rememberLocalDate()
    val term = remember(entity) { entity?.toDomain() }

    if (!loaded) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (courses.isEmpty() || term == null) {
        val title = if (courses.isEmpty()) "还没有课表" else "需要确认开学日期"
        val hint = if (courses.isEmpty()) "登录教务系统，导入本学期的课程。"
            else "在设置的「学期与调休」中填写第 1 周周一。"
        ExpressiveEmptyState(title, hint, modifier, icon = Icons.Outlined.Today,
            onSync = onSync.takeIf { courses.isEmpty() })
        return
    }

    val date = today
    val overrides = settings.dayOverrides
    val todayList = remember(courses, term, date, overrides) { term.coursesOn(date, courses, overrides) }
    val upcoming = remember(courses, term, date, overrides, todayList) {
        if (todayList.isEmpty()) term.nextCourseDay(date, courses, overrides) else null
    }
    val week = term.weekOf(date)
    val dayOverride = DayOverrides.of(date, overrides)
    val title = when {
        dayOverride?.isHoliday == true -> "今天放假"
        todayList.isNotEmpty() -> "今天有 ${todayList.size} 门课"
        week > 0 -> "今天没课"
        date < term.startMonday -> "本学期尚未开始"
        else -> "本学期已结束"
    }
    val subtitle = listOfNotNull(
        if (week > 0) "第 $week 周" else null,
        dayOverride?.useDayOfWeek?.let { "补${DayOverrides.labelOf(it)}的课" },
        term.campus.label,
    ).joinToString(" · ")
    val shownCourses = upcoming?.second ?: todayList

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ExpressiveIntro(
                eyebrow = "${date.monthValue} 月 ${date.dayOfMonth} 日 · ${WEEKDAY_CN[date.dayOfWeek.value - 1]}",
                title = title,
                subtitle = subtitle,
                icon = Icons.Outlined.Today,
            )
        }
        if (upcoming != null) {
            val nextDate = upcoming.first
            item {
                SectionLabel("下次上课", "${nextDate.monthValue} 月 ${nextDate.dayOfMonth} 日 · ${WEEKDAY_CN[nextDate.dayOfWeek.value - 1]}")
            }
        }
        itemsIndexed(
            shownCourses,
            key = { index, course -> if (course.id != 0L) course.id else "$index-${course.slotKey}" },
        ) { _, course -> CourseRow(course, term.campus) }
    }
}

@Composable
private fun CourseRow(course: CourseEvent, campus: Campus, modifier: Modifier = Modifier) {
    val accent = courseColorFor(course.name)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = accent.copy(alpha = 0.16f),
                modifier = Modifier.size(64.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (course.startPeriod == course.endPeriod) "${course.startPeriod}" else "${course.startPeriod}–${course.endPeriod}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text("节", style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(course.name, style = MaterialTheme.typography.titleMedium)
                val time = PeriodTimes.rangeText(campus, course.startPeriod, course.endPeriod)
                if (time.isNotEmpty()) Text(time, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                val sub = listOf(course.room, course.teacher).filter { it.isNotEmpty() }.joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 成绩列表里的一行：学期小标题，或一门课。 */
private sealed interface GradeRow {
    data class Term(val name: String, val stats: GradeStats) : GradeRow
    data class Course(val entity: GradeEntity) : GradeRow
}

private fun creditText(v: Double): String = String.format("%.1f", v)

/**
 * 成绩页顶部的总览 + 按学期分组的行数据。
 *
 * 只在 [GradesScreen] 内部用，做成 private：外面不需要、也不该知道这个中间态。
 */
private data class GradeScreenData(
    val stats: GradeStats,
    val rows: List<GradeRow>,
)

@Composable
fun GradesScreen(vm: MainViewModel, modifier: Modifier = Modifier, onSync: (() -> Unit)? = null) {
    val grades by vm.grades.collectAsState()
    if (grades.isEmpty()) {
        ExpressiveEmptyState("每一份努力，都有记录", "同步成绩，查看学分、绩点与学期明细。", modifier, Icons.Outlined.School, onSync)
        return
    }

    // 总览 + 分学期明细一起算、一起缓存。
    // 原来这三段（GradeSummary.of / groupBy / buildList，其中每个学期还要再 GradeSummary.of 一次）
    // 全内联在 composable 体里，任何一次重组都要重算并重新分配几十个对象；
    // 合并成单个 remember(grades)，保证同一份数据只算一次（拆成两个 remember 会漏掉「byTerm」这层重复遍历）。
    // `byTerm` 只是这个 lambda 内部的中间量，算完就该被回收，不进 holder。
    val data = remember(grades) {
        // 总览：学分绩点和加权平均分是两个指标，分别给出来
        val stats = GradeSummary.of(grades.map { GradeRecord(it.credit, it.score, it.gpa) })

        // 按学期分组，每个学期各算一份 —— 这样能看出"这学期比上学期进步了没有"
        val byTerm = grades.groupBy { it.termName }
        val rows: List<GradeRow> = buildList {
            GradeSummary.orderTerms(byTerm.keys).forEach { term ->
                val list = byTerm[term].orEmpty().sortedBy { it.course }
                add(
                    GradeRow.Term(
                        name = term.ifBlank { "未标注学期" },
                        stats = GradeSummary.of(list.map { GradeRecord(it.credit, it.score, it.gpa) }),
                    )
                )
                list.forEach { add(GradeRow.Course(it)) }
            }
        }
        GradeScreenData(stats = stats, rows = rows)
    }
    val stats = data.stats
    val rows = data.rows

    val entrance = rememberEntranceWindow(rows.size)

    Column(modifier.fillMaxSize()) {
        // ------------------------------------------------ 总览
        Card(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .staggeredAppear(0, animate = entrance),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("平均学分绩点", style = MaterialTheme.typography.labelSmall)
                        Text(
                            if (stats.hasGpa) String.format("%.3f", stats.gpa) else "-",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("已获学分 / 总学分", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${creditText(stats.earnedCredit)} / ${creditText(stats.totalCredit)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth()) {
                    GradeStat("加权平均分", if (stats.hasScore) String.format("%.2f", stats.weightedScore) else "-", Modifier.weight(1f))
                    GradeStat("门数", "${stats.courseCount}", Modifier.weight(1f))
                    GradeStat("不及格", "${stats.failedCount}", Modifier.weight(1f))
                }
            }
        }

        // ------------------------------------------------ 按学期明细
        LazyColumn(
            contentPadding = PaddingValues(
            start = 12.dp,
            top = 4.dp,
            end = 12.dp,
            bottom = 4.dp,
        ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(
                items = rows,
                // 「学期标题」按学期名去重（orderTerms 返回的 key 唯一，空名也只会出现一次）；
                // 「课程」用 Room 主键。前缀区分两类行，避免学期名恰好和某个 id 撞车。
                key = { _, row ->
                    when (row) {
                        is GradeRow.Term -> "t-${row.name}"
                        is GradeRow.Course -> "c-${row.entity.id}"
                    }
                },
                // 两种行高度/结构完全不同，分开声明能让 LazyColumn 按类型复用槽位
                contentType = { _, row ->
                    when (row) {
                        is GradeRow.Term -> "term"
                        is GradeRow.Course -> "course"
                    }
                },
            ) { index, row ->
                when (row) {
                    is GradeRow.Term -> TermHeader(row, index, entrance)
                    is GradeRow.Course -> GradeCourseCard(row.entity, index, entrance)
                }
            }
        }
    }
}

@Composable
private fun GradeStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun TermHeader(row: GradeRow.Term, index: Int, animate: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp)
            .staggeredAppear(index + 1, animate = animate),
    ) {
        Text(
            row.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            buildList {
                add("${creditText(row.stats.totalCredit)} 学分")
                if (row.stats.hasGpa) add("绩点 ${String.format("%.3f", row.stats.gpa)}")
                if (row.stats.failedCount > 0) add("挂 ${row.stats.failedCount} 门")
            }.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun GradeCourseCard(g: GradeEntity, index: Int, animate: Boolean) {
    Card(
        Modifier.fillMaxWidth().staggeredAppear(index + 1, animate = animate),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    g.course,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (g.score > 0) String.format("%.1f", g.score) else "-",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(
                listOf(
                    "${creditText(g.credit)} 学分",
                    if (g.gpa > 0) "绩点 ${g.gpa}" else "",
                    g.kind,
                ).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

// 原先这里还有一个 ClassroomsScreen（空闲教室列表）。
// 学校教务系统禁止访问空闲教室界面，抓不到数据，页面已整体移除，
// 底栏对应标签也一起删掉 —— 而不是留一个永远显示「还没有数据」的空页面。
