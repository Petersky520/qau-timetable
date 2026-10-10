package cn.edu.qau.timetable.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.DayOverrides
import cn.edu.qau.timetable.core.GradeRecord
import cn.edu.qau.timetable.ui.glass.LocalFloatingBarReserve
import cn.edu.qau.timetable.core.GradeStats
import cn.edu.qau.timetable.core.GradeSummary
import cn.edu.qau.timetable.core.PeriodTimes
import cn.edu.qau.timetable.data.model.GradeEntity
import cn.edu.qau.timetable.domain.CourseEvent
import cn.edu.qau.timetable.ui.motion.rememberEntranceWindow
import cn.edu.qau.timetable.ui.motion.staggeredAppear
import java.time.LocalDate

private val WEEKDAY_CN = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@Composable
private fun EmptyState(title: String, hint: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                // 空状态也走同一条入场通道，避免出现"别处都在动、这里硬切"的割裂
                .staggeredAppear(0),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun TodayScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val courses by vm.courses.collectAsState()
    val term by vm.activeTerm.collectAsState()
    val settings by vm.settings.collectAsState()

    val campus: Campus = term?.toDomain()?.campus ?: settings.campus
    val week = vm.currentWeek()
    val today = LocalDate.now()
    val ownDay = today.dayOfWeek.value
    // 调休：补课日按被借的那天的课表上课，放假日没有课
    val override = DayOverrides.of(today, settings.dayOverrides)
    val dow = DayOverrides.effectiveDayOfWeek(today, settings.dayOverrides)

    val todayList: List<CourseEvent> = if (week > 0 && dow != null) {
        courses.filter { it.dayOfWeek == dow && it.occursIn(week) }.sortedBy { it.startPeriod }
    } else {
        emptyList()
    }

    if (week <= 0) {
        EmptyState(
            "不在学期周次内",
            "当前日期推算不出第几周。请在「设置」里填写本学期第 1 周的周一日期。",
        )
        return
    }

    if (dow == null) {
        EmptyState(
            "今天放假",
            "调休安排里把 $today 标成了放假日。",
        )
        return
    }

    val dayLabel = when {
        override == null -> WEEKDAY_CN[ownDay - 1]
        override.isHoliday -> "${WEEKDAY_CN[ownDay - 1]} · 放假"
        else -> "${WEEKDAY_CN[ownDay - 1]} · 补${WEEKDAY_CN[dow - 1]}的课"
    }

    // 入场窗口：只有开屏后这一小段时间里组合出来的项才播动画，
    // 避免滚动来回时卡片反复淡入（LazyColumn 会销毁/重建视口外的项）。
    val entrance = rememberEntranceWindow(todayList.size)

    Column(modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .staggeredAppear(0, animate = entrance),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "${today}  ${dayLabel}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "第 $week 周 · ${campus.label} · 共 ${todayList.size} 节",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (todayList.isEmpty()) {
            EmptyState("今天没课 🎉", "好好休息，或者去图书馆卷一下。")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 12.dp,
                    end = 12.dp,
                    // 底部多让出悬浮底栏那一段，否则最后一条会被底栏永久挡住
                    bottom = 12.dp + LocalFloatingBarReserve.current,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(todayList) { index, course ->
                    // 序号 +1：摘要卡已经占了 0 号，课程从下一拍接上
                    CourseRow(
                        course,
                        campus,
                        modifier = Modifier.staggeredAppear(index + 1, animate = entrance),
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseRow(course: CourseEvent, campus: Campus, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (course.startPeriod == course.endPeriod) {
                        "${course.startPeriod}"
                    } else {
                        "${course.startPeriod}-${course.endPeriod}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("节", style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.weight(1f)) {
                Text(course.name, style = MaterialTheme.typography.titleSmall)
                val time = PeriodTimes.rangeText(campus, course.startPeriod, course.endPeriod)
                if (time.isNotEmpty()) {
                    Text(time, style = MaterialTheme.typography.bodySmall)
                }
                val sub = listOf(course.room, course.teacher).filter { it.isNotEmpty() }.joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun ExamsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val exams by vm.exams.collectAsState()
    if (exams.isEmpty()) {
        EmptyState("还没有考试安排", "在「同步」页登录后抓取一次考试安排。")
        return
    }
    val entrance = rememberEntranceWindow(exams.size)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(exams) { index, exam ->
            Card(Modifier.fillMaxWidth().staggeredAppear(index, animate = entrance)) {
                Column(Modifier.padding(14.dp)) {
                    Text(exam.course, style = MaterialTheme.typography.titleSmall)
                    val line1 = listOf(exam.date, exam.time).filter { it.isNotEmpty() }.joinToString("  ")
                    if (line1.isNotEmpty()) Text(line1, style = MaterialTheme.typography.bodySmall)
                    val line2 = listOf(exam.room, exam.seat).filter { it.isNotEmpty() }.joinToString(" · ")
                    if (line2.isNotEmpty()) Text(line2, style = MaterialTheme.typography.bodySmall)
                    if (exam.note.isNotEmpty()) {
                        Text(exam.note, style = MaterialTheme.typography.labelSmall)
                    }
                }
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

@Composable
fun GradesScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val grades by vm.grades.collectAsState()
    if (grades.isEmpty()) {
        EmptyState("还没有成绩数据", "在「同步」页登录后抓取一次成绩。")
        return
    }

    // 总览：学分绩点和加权平均分是两个指标，分别给出来
    val stats = GradeSummary.of(grades.map { GradeRecord(it.credit, it.score, it.gpa) })

    // 按学期分组，每个学期各算一份 —— 这样能看出"这学期比上学期进步了没有"
    val byTerm = grades.groupBy { it.termName }
    val rows = buildList {
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

    val entrance = rememberEntranceWindow(rows.size)

    Column(modifier.fillMaxSize()) {
        // ------------------------------------------------ 总览
        Card(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .staggeredAppear(0, animate = entrance),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("平均学分绩点", style = MaterialTheme.typography.labelSmall)
                        Text(
                            if (stats.hasGpa) String.format("%.3f", stats.gpa) else "-",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
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
            bottom = 4.dp + LocalFloatingBarReserve.current,
        ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(rows) { index, row ->
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
    Card(Modifier.fillMaxWidth().staggeredAppear(index + 1, animate = animate)) {
        Column(Modifier.padding(12.dp)) {
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

@Composable
fun ClassroomsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val rooms by vm.classrooms.collectAsState()
    if (rooms.isEmpty()) {
        EmptyState("还没有空闲教室数据", "在「同步」页登录后抓取一次空闲教室。")
        return
    }
    val entrance = rememberEntranceWindow(rooms.size)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(rooms) { index, r ->
            Card(Modifier.fillMaxWidth().staggeredAppear(index, animate = entrance)) {
                Column(Modifier.padding(12.dp)) {
                    Text(r.room, style = MaterialTheme.typography.titleSmall)
                    val sub = listOf(r.building, r.campus, if (r.capacity > 0) "容纳 ${r.capacity} 人" else "")
                        .filter { it.isNotEmpty() }.joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
                    if (r.freeSlots.isNotEmpty()) {
                        Text("空闲：${r.freeSlots}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
