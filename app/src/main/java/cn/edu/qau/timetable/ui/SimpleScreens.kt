package cn.edu.qau.timetable.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.EventNote
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
fun TodayScreen(vm: MainViewModel, modifier: Modifier = Modifier, onSync: (() -> Unit)? = null) {
    val courses by vm.courses.collectAsState()
    val term by vm.activeTerm.collectAsState()
    val settings by vm.settings.collectAsState()

    val campus: Campus = term?.toDomain()?.campus ?: settings.campus
    val week = vm.currentWeek()

    // 「今天」只取一次：页面存活期内不跨天，而 `LocalDate.now()` 是读系统时钟的副作用，
    // 放在 composition 里每帧重读既没意义，又会让所有依赖它的派生值每帧都失效。
    val today = remember { LocalDate.now() }
    val ownDay = today.dayOfWeek.value

    // 调休：补课日按被借的那天的课表上课，放假日没有课。
    // `override` 与 `dow` 只依赖「哪一天」+ 调休安排，一起 remember：
    // 原来每次重组都要把 dayOverrides 线性扫两遍。
    val (override, dow) = remember(today, settings.dayOverrides) {
        DayOverrides.of(today, settings.dayOverrides) to
            DayOverrides.effectiveDayOfWeek(today, settings.dayOverrides)
    }

    // 过滤 + 排序只跟「课程数据 / 周次 / 实际星期 / 调休安排」有关。
    // 原来这段内联在 composable 体里，滚动、Snackbar、主题切换等任何重组都会
    // 重新走一遍 filter，且 `occursIn` 每次要查 `WeekPattern.weeks` 集合。
    val todayList: List<CourseEvent> = remember(courses, week, dow, settings.dayOverrides) {
        if (week > 0 && dow != null) {
            courses.filter { it.dayOfWeek == dow && it.occursIn(week) }.sortedBy { it.startPeriod }
        } else {
            emptyList()
        }
    }

    if (week <= 0) {
        // 算不出周次有好几种原因，别一律甩「去设置里填日期」——
        // 放假期间填一百次日期也还是算不出来。
        val termDomain = term?.toDomain()
        val hint = when {
            term == null ->
                "还没导入课表。点右上角「同步」登录教务系统抓取一次。"

            termDomain == null ->
                "「学期」里还没填第 1 周周一，所以推算不出周次。去「设置 → 学期」补上即可。"

            today.isBefore(termDomain.startMonday) ->
                "还没开学。本学期第 1 周是 ${termDomain.startMonday}。"

            else ->
                "本学期课表已经结束了（共 ${termDomain.totalWeeks} 周）。"
        }
        ExpressiveEmptyState("不在学期周次内", hint, modifier, icon = Icons.Outlined.Today, onSync = if (term == null) onSync else null)
        return
    }

    if (dow == null) {
        ExpressiveEmptyState("今天放假", "调休安排里把 $today 标成了放假日。", modifier, icon = Icons.Outlined.Today)
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
        ExpressiveIntro(
            eyebrow = "${today.monthValue} 月 ${today.dayOfMonth} 日 · $dayLabel",
            title = "今天有 ${todayList.size} 节课",
            subtitle = "第 $week 周 · ${campus.label}",
            icon = Icons.Outlined.Today,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                .staggeredAppear(0, animate = entrance),
        )

        if (todayList.isEmpty()) {
            ExpressiveEmptyState("今天，留点时间给自己", "没有课程安排，好好休息或去探索校园吧。", icon = Icons.Outlined.Today)
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
                itemsIndexed(
                    items = todayList,
                    // Room 主键是稳定的，滚动出视口再回来能复用同一个 item；
                    // id 为 0（上游临时构造、还没落库）时退回「位置 + 节次格」，至少不会撞 key。
                    key = { index, course ->
                        if (course.id != 0L) course.id else "$index-${course.slotKey}"
                    },
                    // 这里只有一种卡片，显式声明 contentType 让 LazyColumn 少建一条类型分支
                    contentType = { _, _ -> "course" },
                ) { index, course ->
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

@Composable
fun ExamsScreen(vm: MainViewModel, modifier: Modifier = Modifier, onSync: (() -> Unit)? = null) {
    val exams by vm.exams.collectAsState()
    if (exams.isEmpty()) {
        ExpressiveEmptyState("考试安排，一目了然", "同步教务数据，查看考试时间与考场。", modifier, Icons.Outlined.EventNote, onSync)
        return
    }
    val entrance = rememberEntranceWindow(exams.size)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + LocalFloatingBarReserve.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "exam-summary", contentType = "summary") {
            ExpressiveIntro("考试日程", "从容应考", "共 ${exams.size} 项考试安排 · 提前确认时间与考场", Icons.Outlined.EventNote)
        }
        itemsIndexed(
            items = exams,
            // exam.id 是 Room 自增主键，全局唯一：滚动复用时动画状态不会串行。
            key = { _, exam -> "e-${exam.id}" },
            contentType = { _, _ -> "exam" },
        ) { index, exam ->
            Card(
                Modifier.fillMaxWidth().staggeredAppear(index + 1, animate = entrance),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(exam.course, style = MaterialTheme.typography.titleMedium)
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
            bottom = 4.dp + LocalFloatingBarReserve.current,
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
