package cn.edu.qau.timetable.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.DayOverride
import cn.edu.qau.timetable.core.DayOverrides
import cn.edu.qau.timetable.core.PeriodTimes
import cn.edu.qau.timetable.domain.CourseEvent
import cn.edu.qau.timetable.domain.coursesOn
import cn.edu.qau.timetable.ui.motion.LocalMotionEnabled
import cn.edu.qau.timetable.ui.motion.QauMotion
import cn.edu.qau.timetable.ui.motion.motionOf
import cn.edu.qau.timetable.ui.theme.courseColorFor
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.absoluteValue

private data class CourseConflictGroup(
    val startPeriod: Int,
    val endPeriod: Int,
    val courses: List<CourseEvent>,
)

private data class DaySchedule(
    val dayOfWeek: Int,
    val date: LocalDate,
    val override: DayOverride?,
    val courses: List<CourseEvent>,
    val groups: List<CourseConflictGroup>,
)

private data class CourseDialogState(
    val date: LocalDate,
    val dayOverride: DayOverride?,
    val courses: List<CourseEvent>,
    val focusedCourseId: Long,
)

@Composable
fun TimetableScreen(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    onSync: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) {
    val courses by vm.courses.collectAsState()
    val activeTermEntity by vm.activeTerm.collectAsState()
    val settings by vm.settings.collectAsState()
    val timetableLoaded by vm.timetableLoaded.collectAsState()

    val term = remember(activeTermEntity) { activeTermEntity?.toDomain() }
    val isWeekGrid = settings.timetableWeekGrid
    val today = rememberLocalDate()
    var selectedDay by rememberSaveable { mutableIntStateOf(today.dayOfWeek.value.coerceIn(1, 7)) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var dialogState by remember { mutableStateOf<CourseDialogState?>(null) }

    LaunchedEffect(activeTermEntity?.id) {
        showWeekPicker = false
        dialogState = null
    }

    val totalWeeks = term?.totalWeeks?.coerceAtLeast(1) ?: 1
    val currentWeek = term?.weekOf(today) ?: 0
    val defaultWeek = when {
        currentWeek > 0 -> currentWeek
        term != null && today.isAfter(term.dateOf(totalWeeks, 7)) -> totalWeeks
        else -> 1
    }
    val pager = key(activeTermEntity?.id, totalWeeks) {
        rememberPagerState(
            initialPage = (vm.weekOverride.value.takeIf { it > 0 } ?: defaultWeek).coerceIn(1, totalWeeks) - 1,
            pageCount = { totalWeeks },
        )
    }
    val scrollScope = rememberCoroutineScope()
    val motionEnabled = LocalMotionEnabled.current
    val pageSpring = motionOf(QauMotion.Spatial)
    val fade = motionOf(QauMotion.Effects)
    val fadeFast = motionOf(QauMotion.EffectsFast)
    val sizeSpring = motionOf(QauMotion.SpatialFast)
    val displayWeek = pager.currentPage + 1

    LaunchedEffect(pager, defaultWeek) {
        if (vm.weekOverride.value == 0 && pager.currentPage != defaultWeek - 1) {
            pager.scrollToPage(defaultWeek - 1)
        }
        snapshotFlow { pager.settledPage }.collect { index ->
            val week = index + 1
            vm.setWeek(if (week == defaultWeek) 0 else week)
        }
    }

    fun scrollToWeek(week: Int) {
        val page = week.coerceIn(1, totalWeeks) - 1
        scrollScope.launch {
            if (motionEnabled) pager.animateScrollToPage(page, animationSpec = pageSpring)
            else pager.scrollToPage(page)
        }
    }

    val onBackToCurrentWeek: () -> Unit = {
        selectedDay = today.dayOfWeek.value
        scrollToWeek(defaultWeek)
    }

    Column(modifier.fillMaxSize()) {
        TimetableToolbar(
            week = displayWeek.takeIf { timetableLoaded && term != null && courses.isNotEmpty() },
            canGoPrevious = pager.targetPage > 0,
            canGoNext = pager.targetPage < totalWeeks - 1,
            canReturnToCurrent = currentWeek > 0 && displayWeek != currentWeek,
            isWeekGrid = isWeekGrid,
            onToggleView = { vm.setTimetableWeekGrid(!isWeekGrid) },
            onPrevious = { scrollToWeek(pager.targetPage) },
            onNext = { scrollToWeek(pager.targetPage + 2) },
            onWeekPicker = { showWeekPicker = true },
            onCurrent = onBackToCurrentWeek,
            onSync = onSync,
            onSettings = onSettings,
        )

        if (!timetableLoaded) {
            TimetableLoadingState()
            return@Column
        }

        if (term == null) {
            ExpressiveEmptyState(
                title = if (courses.isEmpty()) "还没有课表" else "需要确认开学日期",
                hint = if (courses.isEmpty()) "登录教务系统，导入本学期的课程。"
                    else "在设置的「学期与调休」中填写第 1 周周一。",
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Rounded.DateRange,
                onSync = onSync,
            )
            return@Column
        }

        if (courses.isEmpty()) {
            ExpressiveEmptyState(
                title = "本学期暂无课程",
                hint = "${term.name} 暂无课表记录，可点击同步获取最新课表",
                modifier = Modifier.fillMaxSize(),
                icon = Icons.Rounded.DateRange,
                onSync = onSync,
            )
            return@Column
        }

        val campus = term.campus
        val overrides = settings.dayOverrides
        val periodCount = remember(courses) {
            maxOf(PeriodTimes.PERIOD_COUNT, courses.maxOfOrNull { maxOf(it.startPeriod, it.endPeriod) } ?: 0)
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            flingBehavior = PagerDefaults.flingBehavior(state = pager, snapAnimationSpec = pageSpring),
        ) { page ->
            val week = page + 1
            val weekDays = remember(term, week, courses, overrides, periodCount) {
                (1..7).map { dow ->
                    val date = term.dateOf(week, dow)
                    val override = DayOverrides.of(date, overrides)
                    val dayCourses = term.coursesOn(date, courses, overrides)
                    DaySchedule(dow, date, override, dayCourses, buildConflictGroups(dayCourses, periodCount))
                }
            }

            AnimatedContent(
                targetState = isWeekGrid,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    if (motionEnabled) {
                        val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
                            .absoluteValue.coerceIn(0f, 1f)
                        alpha = 1f - offset * 0.14f
                    }
                },
                transitionSpec = {
                    (fadeIn(fade) + scaleIn(sizeSpring, initialScale = 0.985f)) togetherWith
                        (fadeOut(fadeFast) + scaleOut(sizeSpring, targetScale = 0.985f))
                },
                label = "Timetable view",
            ) { weekGrid ->
                if (weekGrid) {
                    WeekGridView(
                        weekDays = weekDays,
                        today = today,
                        campus = campus,
                        periodCount = periodCount,
                        selectedDay = selectedDay,
                        onSelectDay = { selectedDay = it },
                        onGroupClick = { day, group ->
                            dialogState = CourseDialogState(
                                day.date, day.override, group.courses, group.courses.first().id,
                            )
                        },
                    )
                } else {
                    DayListView(
                        weekDays = weekDays,
                        today = today,
                        campus = campus,
                        periodCount = periodCount,
                        selectedDay = selectedDay,
                        onSelectDay = { selectedDay = it },
                        onCourseClick = { day, group, course ->
                            dialogState = CourseDialogState(day.date, day.override, group.courses, course.id)
                        },
                    )
                }
            }
        }

        if (showWeekPicker) {
            WeekPickerDialog(
                totalWeeks = totalWeeks,
                selectedWeek = displayWeek,
                currentWeek = currentWeek,
                onSelectWeek = ::scrollToWeek,
                onBackToCurrent = onBackToCurrentWeek,
                onDismiss = { showWeekPicker = false },
            )
        }

        dialogState?.let { state ->
            CourseDetailDialog(
                state = state,
                campus = campus,
                periodCount = periodCount,
                onDismiss = { dialogState = null },
            )
        }
    }
}

@Composable
private fun TimetableLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun TimetableToolbar(
    week: Int?,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    canReturnToCurrent: Boolean,
    isWeekGrid: Boolean,
    onToggleView: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onWeekPicker: () -> Unit,
    onCurrent: () -> Unit,
    onSync: (() -> Unit)?,
    onSettings: (() -> Unit)?,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val spatial = motionOf(QauMotion.SlideSpatialFast)
    val layout = motionOf(QauMotion.LayoutFast)
    val enter = motionOf(QauMotion.Effects)
    val exit = motionOf(QauMotion.EffectsFast)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (week == null) {
            Text("青小农", Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleLarge)
        } else {
            IconButton(onClick = onPrevious, enabled = canGoPrevious) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "上一周")
            }
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Row(
                    Modifier.weight(1f, fill = false)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(onClickLabel = "选择周次", onClick = onWeekPicker)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    AnimatedContent(
                        targetState = week,
                        modifier = Modifier.weight(1f, fill = false),
                        transitionSpec = {
                            val direction = if (targetState > initialState) 1 else -1
                            (slideInVertically(spatial) { direction * it / 2 } + fadeIn(enter)) togetherWith
                                (slideOutVertically(spatial) { -direction * it / 2 } + fadeOut(exit))
                        },
                        label = "Week number",
                    ) { displayed ->
                        Text(
                            "第 $displayed 周",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(16.dp))
                }
                AnimatedVisibility(
                    visible = canReturnToCurrent,
                    enter = expandHorizontally(layout, expandFrom = Alignment.Start) + fadeIn(enter),
                    exit = shrinkHorizontally(layout, shrinkTowards = Alignment.Start) + fadeOut(exit),
                ) {
                    TextButton(
                        onClick = onCurrent,
                        enabled = canReturnToCurrent,
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Text("本周", maxLines = 1)
                    }
                }
            }
            IconButton(onClick = onNext, enabled = canGoNext) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "下一周")
            }
            IconButton(onClick = onToggleView) {
                AnimatedContent(
                    targetState = isWeekGrid,
                    transitionSpec = { fadeIn(enter) togetherWith fadeOut(exit) },
                    label = "View icon",
                ) { weekGrid ->
                    Icon(
                        if (weekGrid) Icons.Outlined.ViewAgenda else Icons.Outlined.ViewWeek,
                        if (weekGrid) "切换为日程" else "切换为周课表",
                    )
                }
            }
        }
        if (onSync != null || onSettings != null) {
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Rounded.MoreVert, "课表菜单") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (onSync != null) DropdownMenuItem(
                        text = { Text("同步课表") },
                        onClick = {
                            menuExpanded = false
                            onSync()
                        },
                    )
                    if (onSettings != null) DropdownMenuItem(
                        text = { Text("设置") },
                        onClick = {
                            menuExpanded = false
                            onSettings()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayListView(
    weekDays: List<DaySchedule>,
    today: LocalDate,
    campus: Campus,
    periodCount: Int,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    onCourseClick: (DaySchedule, CourseConflictGroup, CourseEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val motionEnabled = LocalMotionEnabled.current
    LaunchedEffect(selectedDay, motionEnabled) {
        val index = selectedDay - 1
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            if (motionEnabled) listState.animateScrollToItem(index) else listState.scrollToItem(index)
        }
    }
    val color = motionOf(QauMotion.ColorEffects)
    val fade = motionOf(QauMotion.Effects)
    val fadeFast = motionOf(QauMotion.EffectsFast)
    val spatial = motionOf(QauMotion.SlideSpatialFast)
    val layoutSign = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    Column(modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(weekDays, key = { it.dayOfWeek }) { item ->
                val selected = item.dayOfWeek == selectedDay
                val colors = MaterialTheme.colorScheme
                val background by animateColorAsState(
                    if (selected) colors.primary else colors.surfaceContainer, color, label = "Selected day",
                )
                val foreground by animateColorAsState(
                    if (selected) colors.onPrimary else colors.onSurface, color, label = "Day text",
                )
                Surface(
                    selected = selected,
                    onClick = { onSelectDay(item.dayOfWeek) },
                    shape = MaterialTheme.shapes.large,
                    color = background,
                    contentColor = foreground,
                    modifier = Modifier.widthIn(min = 48.dp),
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (item.date == today) "今天" else DayOverrides.labelOf(item.dayOfWeek), style = MaterialTheme.typography.labelMedium)
                        Text("${item.date.monthValue}/${item.date.dayOfMonth}", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        AnimatedContent(
            targetState = selectedDay,
            modifier = Modifier.fillMaxWidth().weight(1f),
            transitionSpec = {
                val direction = if (targetState > initialState) layoutSign else -layoutSign
                (slideInHorizontally(spatial) { direction * it / 12 } + fadeIn(fade)) togetherWith
                    (slideOutHorizontally(spatial) { -direction * it / 12 } + fadeOut(fadeFast))
            },
            label = "Selected day schedule",
        ) { dayOfWeek ->
            val day = weekDays[dayOfWeek - 1]
            val change = day.override
            val subtitle = when {
                change?.isHoliday == true -> "放假"
                change?.useDayOfWeek != null -> "补${DayOverrides.labelOf(change.useDayOfWeek)}的课"
                else -> null
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (subtitle != null) item { SectionLabel(subtitle) }
                if (day.courses.isEmpty() && change?.isHoliday != true) item { SectionLabel("当天没有课程安排") }
                day.groups.forEach { group ->
                    items(group.courses) { course ->
                        DayCourseCard(course, campus, periodCount, group.courses.size) { onCourseClick(day, group, course) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCourseCard(
    course: CourseEvent,
    campus: Campus,
    periodCount: Int,
    count: Int,
    onClick: () -> Unit,
) {
    val (start, end) = safePeriodBounds(course, periodCount)
    val time = PeriodTimes.rangeText(campus, start, end)
    val accent = courseColorFor(course.name)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = accent.copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surfaceContainerLow),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Text(
                    formatPeriodRange(start, end),
                    Modifier.padding(10.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(course.name, style = MaterialTheme.typography.titleMedium)
                if (time.isNotBlank()) Text(time, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                val location = listOf(course.room, course.teacher).filter(String::isNotBlank).joinToString(" · ")
                if (location.isNotBlank()) Text(location, style = MaterialTheme.typography.bodySmall)
                if (count > 1) Text("时间重叠 · $count 门课", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun WeekGridView(
    weekDays: List<DaySchedule>,
    today: LocalDate,
    campus: Campus,
    periodCount: Int,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    onGroupClick: (DaySchedule, CourseConflictGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val axisWidth = 34.dp
    val slotHeight = 56.dp
    val totalHeight = slotHeight * periodCount

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(axisWidth),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${weekDays.first().date.monthValue}月",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            weekDays.forEach { day ->
                val isToday = day.date == today
                val isSelected = day.dayOfWeek == selectedDay
                val headerBg = when {
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    isSelected -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> Color.Transparent
                }
                val headerFg = if (isToday) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(headerBg)
                        .clickable { onSelectDay(day.dayOfWeek) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = DayOverrides.labelOf(day.dayOfWeek),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                        color = headerFg,
                        maxLines = 1,
                    )
                    Text(
                        text = "${day.date.monthValue}/${day.date.dayOfMonth}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = headerFg.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                    val mark = when {
                        day.override?.isHoliday == true -> "休"
                        day.override?.useDayOfWeek != null -> "补" + singleCharWeekday(day.override.useDayOfWeek)
                        else -> ""
                    }
                    if (mark.isNotEmpty()) Text(mark, style = MaterialTheme.typography.labelSmall, maxLines = 1)

                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 6.dp, end = 6.dp, bottom = 12.dp),
        ) {
            Column(modifier = Modifier.width(axisWidth)) {
                for (period in 1..periodCount) {
                    val startText = PeriodTimes.get(campus, period)?.startText.orEmpty()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(slotHeight),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "$period",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        if (startText.isNotBlank()) {
                            Text(
                                text = startText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(totalHeight),
            ) {
                weekDays.forEach { day ->
                    WeekGridDayColumn(
                        day = day,
                        isToday = day.date == today,
                        periodCount = periodCount,
                        slotHeight = slotHeight,
                        onGroupClick = { group -> onGroupClick(day, group) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}


@Composable
private fun WeekGridDayColumn(
    day: DaySchedule,
    isToday: Boolean,
    periodCount: Int,
    slotHeight: Dp,
    onGroupClick: (CourseConflictGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isHoliday = day.override?.isHoliday == true
    val columnBg = when {
        isHoliday -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.18f)
        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.045f)
        else -> Color.Transparent
    }
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
    val sectionDividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)

    Box(
        modifier = modifier.background(columnBg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (period in 1..periodCount) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(slotHeight),
                ) {
                    HorizontalDivider(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        thickness = if (period == 4 || period == 8) 1.dp else 0.5.dp,
                        color = if (period == 4 || period == 8) sectionDividerColor else dividerColor,
                    )
                }
            }
        }

        if (isHoliday && day.groups.isEmpty()) {
            Text(
                text = "放\n假",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = slotHeight * 2),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        }

        day.groups.forEach { group ->
            val clampedStart = group.startPeriod.coerceIn(1, periodCount)
            val clampedEnd = group.endPeriod.coerceIn(clampedStart, periodCount)
            val span = (clampedEnd - clampedStart + 1).coerceAtLeast(1)
            val topPadding = slotHeight * (clampedStart - 1)
            val blockHeight = slotHeight * span

            WeekGridCourseBlock(
                group = group,
                span = span,
                onClick = { onGroupClick(group) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding)
                    .height(blockHeight)
                    .padding(1.5.dp),
            )
        }
    }
}

@Composable
private fun WeekGridCourseBlock(
    group: CourseConflictGroup,
    span: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = group.courses.first()
    val accent = courseColorFor(primary.name)
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    val bgColor = accent.copy(alpha = 0.17f).compositeOver(surface)

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(0.75.dp, accent.copy(alpha = 0.42f)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 3.dp, end = 3.dp, top = 4.dp, bottom = 3.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = primary.name,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = when {
                            span <= 1 -> 2
                            span == 2 -> 4
                            else -> 6
                        },
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (primary.room.isNotBlank()) {
                        Text(
                            text = "@${primary.room}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (span <= 1) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (group.courses.size > 1) {
                    Surface(
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(5.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = "+${group.courses.size - 1}",
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                lineHeight = 10.sp,
                            ),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekPickerDialog(
    totalWeeks: Int,
    selectedWeek: Int,
    currentWeek: Int,
    onSelectWeek: (Int) -> Unit,
    onDismiss: () -> Unit,
    onBackToCurrent: () -> Unit,
) {
    TimetableSheet("选择周次", onDismiss) { close ->
        LazyColumn(
            modifier = Modifier.heightIn(max = 360.dp),
            state = rememberLazyListState(initialFirstVisibleItemIndex = (selectedWeek - 3).coerceAtLeast(0)),
        ) {
            items(totalWeeks) { index ->
                val week = index + 1
                Surface(
                    selected = selectedWeek == week,
                    onClick = {
                        onSelectWeek(week)
                        close()
                    },
                    shape = MaterialTheme.shapes.medium,
                    color = if (selectedWeek == week) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        "第 $week 周" + if (currentWeek == week) " · 本周" else "",
                        Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
            }
        }
        if (currentWeek > 0) {
            TextButton(onClick = {
                onBackToCurrent()
                close()
            }) { Text("本周") }
        }
    }
}

@Composable
private fun CourseDetailDialog(
    state: CourseDialogState,
    campus: Campus,
    periodCount: Int,
    onDismiss: () -> Unit,
) {
    val ordered = state.courses.sortedBy { it.id != state.focusedCourseId }
    val date = state.date
    val dateLabel = "${date.monthValue} 月 ${date.dayOfMonth} 日 · ${DayOverrides.labelOf(date.dayOfWeek.value)}"
    val title = if (ordered.size > 1) "时间重叠 · ${ordered.size} 门课" else "课程详情"
    TimetableSheet(title, onDismiss) { close ->
        Column(
            Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val source = state.dayOverride?.useDayOfWeek?.let { " · 补${DayOverrides.labelOf(it)}的课" }.orEmpty()
            Text(dateLabel + source, style = MaterialTheme.typography.bodySmall)
            ordered.forEach { course ->
                val (start, end) = safePeriodBounds(course, periodCount)
                val time = PeriodTimes.rangeText(campus, start, end)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(course.name, style = MaterialTheme.typography.titleMedium)
                        Text(listOf(formatPeriodRange(start, end), time).filter(String::isNotBlank).joinToString(" · "))
                        if (course.room.isNotBlank()) Text(course.room)
                        if (course.teacher.isNotBlank()) Text(course.teacher)
                        Text(course.pattern.display(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        TextButton(onClick = close, modifier = Modifier.align(Alignment.End)) { Text("关闭") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimetableSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            content {
                scope.launch {
                    state.hide()
                    if (!state.isVisible) onDismiss()
                }
            }
        }
    }
}

private fun safePeriodBounds(course: CourseEvent, periodCount: Int): Pair<Int, Int> {
    val maxPeriod = periodCount.coerceAtLeast(1)
    val rawStart = minOf(course.startPeriod, course.endPeriod)
    val rawEnd = maxOf(course.startPeriod, course.endPeriod)
    val start = rawStart.coerceIn(1, maxPeriod)
    val end = rawEnd.coerceIn(start, maxPeriod)
    return start to end
}

private fun buildConflictGroups(
    courses: List<CourseEvent>,
    periodCount: Int,
): List<CourseConflictGroup> {
    if (courses.isEmpty()) return emptyList()
    val sorted = courses
        .map { course ->
            val (start, end) = safePeriodBounds(course, periodCount)
            Triple(course, start, end)
        }
        .sortedWith(
            compareBy<Triple<CourseEvent, Int, Int>> { it.second }
                .thenByDescending { it.third - it.second }
                .thenBy { it.first.id },
        )

    val groups = mutableListOf<CourseConflictGroup>()
    var curStart = sorted[0].second
    var curEnd = sorted[0].third
    var bucket = mutableListOf(sorted[0].first)

    for (i in 1 until sorted.size) {
        val (course, start, end) = sorted[i]
        if (start <= curEnd) {
            curEnd = maxOf(curEnd, end)
            bucket.add(course)
        } else {
            groups.add(CourseConflictGroup(curStart, curEnd, bucket))
            curStart = start
            curEnd = end
            bucket = mutableListOf(course)
        }
    }
    groups.add(CourseConflictGroup(curStart, curEnd, bucket))
    return groups
}

private fun formatPeriodRange(startPeriod: Int, endPeriod: Int): String {
    val start = minOf(startPeriod, endPeriod)
    val end = maxOf(startPeriod, endPeriod)
    return if (start == end) "第${start}节" else "第${start}-${end}节"
}

private fun singleCharWeekday(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    7 -> "日"
    else -> "$dayOfWeek"
}
