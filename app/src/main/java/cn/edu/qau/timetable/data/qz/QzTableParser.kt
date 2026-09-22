package cn.edu.qau.timetable.data.qz

import cn.edu.qau.timetable.core.PeriodTimes
import cn.edu.qau.timetable.core.WeekPattern
import cn.edu.qau.timetable.core.WeekPatternParser
import cn.edu.qau.timetable.domain.ClassroomItem
import cn.edu.qau.timetable.domain.CourseEvent
import cn.edu.qau.timetable.domain.ExamItem
import cn.edu.qau.timetable.domain.GradeItem

/**
 * 强智教务系统（jsxsd）表格解析。
 *
 * 设计原则：**不依赖具体 CSS 类名**，因为强智各校部署的模板差异很大，
 * 而且一改版类名就变。我们只依赖两件稳定的事：
 *   1. 课表是一张「节次 × 星期」的表格；
 *   2. 单元格里用换行分隔若干行文本，有些行带 `title` 属性标明语义。
 *
 * JS 侧（见 QzJs.kt）会把 DOM 表格摊平成一个等长二维数组，并对
 * rowspan/colspan 做**复制填充**，这里再做「连续相同 ⇒ 合并」。
 *
 * 带语义的行被 JS 编码成 `属性名\u0001值`，例如 `周次\u000112-16周`。
 * 没有 title 的行就是普通文本行，靠启发式判断。
 */
object QzTableParser {

    /** JS 侧用来分隔「属性名」和「值」。 */
    const val TITLE_SEP = '\u0001'

    /**
     * 允许的最大节次。
     * 真实学校最多十几节，30 是安全上限 —— 用来保证 coerceIn 的区间永远有效。
     */
    const val MAX_PERIOD = 30

    private val DASH_LINE = Regex("""^[\s\-—_=*·.~]+$""")
    private val PERIOD_RANGE = Regex("""(?:第)?\s*(\d{1,2})\s*[-~～—–至]\s*(\d{1,2})\s*节""")
    private val PERIOD_SINGLE = Regex("""(?:第)?\s*(\d{1,2})\s*节""")
    private val ANY_NUMBER = Regex("""\d{1,2}""")
    private val WEEK_HINT = Regex("""\d{1,2}\s*(?:[-~～—–至]\s*\d{1,2})?(?:\s*[,，]\s*\d{1,2}\s*(?:[-~～—–至]\s*\d{1,2})?)*\s*周(?:\([单双]\)|（[单双]）)?""")

    private val DAY_TOKENS: List<Pair<String, Int>> = listOf(
        "星期一" to 1, "星期二" to 2, "星期三" to 3, "星期四" to 4,
        "星期五" to 5, "星期六" to 6, "星期日" to 7, "星期天" to 7,
        "周一" to 1, "周二" to 2, "周三" to 3, "周四" to 4,
        "周五" to 5, "周六" to 6, "周日" to 7, "周天" to 7,
        "礼拜一" to 1, "礼拜二" to 2, "礼拜三" to 3, "礼拜四" to 4,
        "礼拜五" to 5, "礼拜六" to 6, "礼拜日" to 7,
    )

    // ------------------------------------------------------------------ 课表

    /** 把摊平后的表格解析成课程事件（没有 rowspan 信息时靠文本合并兜底）。 */
    fun parseTimetable(rows: List<List<String>>): List<CourseEvent> =
        parseTimetable(rows, emptyList())

    /**
     * @param rowspans 与 [rows] 同形；锚点处是该格纵向跨的行数（占几小节），其余为 0。
     *   **这是判断"一门课占几小节"的权威依据。**
     *   为空时退回"相邻文本相同就合并"的启发式（不如前者可靠）。
     */
    fun parseTimetable(
        rows: List<List<String>>,
        rowspans: List<List<Int>>,
    ): List<CourseEvent> {
        if (rows.isEmpty()) return emptyList()

        val cleaned = rows.map { row -> row.map { normalizeCell(it) } }
        val columns = detectDayColumns(cleaned)
        if (columns.map.isEmpty()) return emptyList()

        // 节次列 = 第一天所在列的**左边一列**。
        // 绝不能假定它是第 0 列：有些部署课表首列不是节次，
        // 一旦按行号兜底递增就会一路涨到 12，进而让
        // coerceIn(12, 11) 抛 IllegalArgumentException。
        val firstDayCol = columns.map.keys.minOrNull() ?: 0
        val periodColumn = if (firstDayCol > 0) firstDayCol - 1 else -1

        // 表头行本身不是课程数据，必须跳过。
        // 若表头没被识别出来（星期名不在一行、或用了非文本渲染），
        // 就用「节次列第一次出现 1」来定位数据起始行 ——
        // 否则整张表会整体后移一位，表现为所有课都往下串了一节。
        var firstDataRow = if (columns.headerRow >= 0) columns.headerRow + 1 else 0
        if (columns.headerRow < 0 && periodColumn >= 0) {
            val idx = cleaned.indexOfFirst { row ->
                val raw = row.getOrNull(periodColumn).orEmpty().trim()
                parsePeriod(raw)?.first == 1 || raw == "1"
            }
            if (idx > 0) firstDataRow = idx
        }

        val rowPeriods = detectRowPeriods(cleaned, periodColumn, firstDataRow)

        val useSpans = rowspans.size == cleaned.size && rowspans.any { row -> row.any { it > 0 } }

        val out = ArrayList<CourseEvent>()

        for ((col, day) in columns.map) {
            var r = firstDataRow
            while (r < cleaned.size) {
                val text = cleaned[r].getOrNull(col).orEmpty()
                if (text.isBlank()) {
                    r++
                    continue
                }

                val declaredSpan = if (useSpans) rowspans.getOrNull(r)?.getOrNull(col) ?: 0 else 0

                val end = when {
                    // rowspan > 1：权威依据，直接用
                    declaredSpan > 1 -> (r + declaredSpan - 1).coerceAtMost(cleaned.size - 1)

                    // 其余情况**一律**按"归一化文本相同"合并。
                    // 注意 declaredSpan == 1 也必须走这里 ——
                    // 强智经常根本不写 rowspan，而是把同一门课在同一列的相邻格
                    // 里重复写一遍。上一版把 span==1 直接当单节，结果一门两节的课
                    // 被压成了最后一节（"每节课都只剩一节"就是这个原因）。
                    else -> {
                        val key = mergeKey(text)
                        var e = r
                        while (e + 1 < cleaned.size) {
                            val next = cleaned[e + 1].getOrNull(col).orEmpty()
                            if (next.isBlank()) break
                            if (mergeKey(next) != key) break
                            e++
                        }
                        e
                    }
                }

                val rowStart = rowPeriods.getOrNull(r)?.first ?: 0
                val rowEnd = rowPeriods.getOrNull(end)?.second ?: rowStart
                if (rowStart > 0 && rowEnd >= rowStart) {
                    for (entry in parseCellEntries(text)) {
                        // 节次以「行跨度」为准（rowspan，或退回的文本合并结果）。
                        // 单元格里若另外写了 "第1-2节"，只用来把范围放宽，绝不缩短 ——
                        // 宁可显示得长一点，也不要把一门两节的课切成单节。
                        val s = minOf(rowStart, entry.startPeriod ?: rowStart)
                            .coerceIn(1, MAX_PERIOD)
                        val e = maxOf(rowEnd, entry.endPeriod ?: rowEnd)
                            .coerceIn(s, MAX_PERIOD)
                        out += CourseEvent(
                            name = entry.name,
                            teacher = entry.teacher,
                            room = entry.room,
                            dayOfWeek = day,
                            startPeriod = s,
                            endPeriod = e,
                            pattern = entry.weeks,
                        )
                    }
                }
                r = end + 1
            }
        }

        return dedupe(out)
    }

    /** 纯节次行，例如 "1" / "第1节" / "1-2节"。它既不是课程名也不是教师。 */
    private val PERIOD_ONLY_LINE =
        Regex("""^第?\s*\d{1,2}(?:\s*[-~～—–至,，、]\s*\d{1,2})*\s*节?$""")

    /**
     * 判断"相邻两格是不是同一门课"用的归一化文本。
     *
     * 强智有时会在跨节的每一格里都写上节次（第1节 / 第2节），
     * 这时两格原始文本不同，但其实是同一门课 —— 比较前把纯节次行去掉。
     */
    fun mergeKey(text: String): String {
        val key = text.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !PERIOD_ONLY_LINE.matches(it) }
            .joinToString("\n")
        return key.ifBlank { text.trim() }
    }

    private fun dedupe(list: List<CourseEvent>): List<CourseEvent> {
        val seen = HashSet<String>()
        return list.filter {
            val k = "${it.name}|${it.teacher}|${it.room}|${it.dayOfWeek}|${it.startPeriod}|${it.endPeriod}|${it.pattern.toStorage()}"
            seen.add(k)
        }
    }

    /** 星期列映射，以及表头所在行（-1 表示没找到表头）。 */
    private data class DayColumns(val map: Map<Int, Int>, val headerRow: Int)

    /** 找表头行，得出「第几列 = 星期几」。找不到就默认第 1..7 列。 */
    private fun detectDayColumns(rows: List<List<String>>): DayColumns {
        var best: Map<Int, Int> = emptyMap()
        var bestRow = -1
        rows.forEachIndexed { rowIndex, row ->
            val m = LinkedHashMap<Int, Int>()
            row.forEachIndexed { idx, cell ->
                val day = dayOf(cell)
                if (day != null && !m.containsValue(day)) m[idx] = day
            }
            if (m.size > best.size) {
                best = m
                bestRow = rowIndex
            }
        }
        if (best.size >= 3) return DayColumns(best, bestRow)

        // 兜底：默认第 0 列是节次，第 1..7 列是周一到周日
        val fallback = LinkedHashMap<Int, Int>()
        for (d in 1..7) fallback[d] = d
        return DayColumns(fallback, -1)
    }

    private fun dayOf(cell: String): Int? {
        val t = cell.trim()
        if (t.isEmpty() || t.length > 6) return null
        return DAY_TOKENS.firstOrNull { t.contains(it.first) }?.second
    }

    /**
     * 每行的节次区间。
     *
     * 两条路径：
     *  ① 节次列能读出内容 → 用它；个别读不出的行用「上一行末尾 + 1」补。
     *  ② 节次列读不出来（首列不是节次）→ 课表通常**一行一节**，
     *     按数据行序递增。这是兜底，但结果有界（最多行数），不会失控。
     */
    private fun detectRowPeriods(
        rows: List<List<String>>,
        periodColumn: Int,
        firstDataRow: Int,
    ): List<Pair<Int, Int>> {
        val n = rows.size
        val out = MutableList(n) { 0 to 0 }

        val parsed = arrayOfNulls<Pair<Int, Int>>(n)
        var hits = 0
        if (periodColumn >= 0) {
            for (i in firstDataRow until n) {
                val raw = rows[i].getOrNull(periodColumn).orEmpty()
                val p = parsePeriod(raw)
                    ?: raw.trim().toIntOrNull()
                        ?.takeIf { it in 1..MAX_PERIOD }
                        ?.let { it to it }
                parsed[i] = p
                if (p != null) hits++
            }
        }

        val dataRows = (n - firstDataRow).coerceAtLeast(1)

        if (hits * 2 >= dataRows) {
            var lastEnd = 0
            for (i in 0 until n) {
                val raw = rows[i].getOrNull(periodColumn).orEmpty()
                val pair = when {
                    i < firstDataRow -> 0 to 0
                    parsed[i] != null -> parsed[i]!!
                    // 标签为空 → 视为上一组的延续（rowspan 复制填充后的样子）
                    raw.isBlank() -> (lastEnd + 1).let { it to it }
                    // 标签非空但读不出节次（例如「备注:」）→ 不是课程行，跳过。
                    // 不能再拿"上一行 + 1"兜底，否则备注行会变成第 12 节的一堆假课。
                    else -> 0 to 0
                }
                out[i] = pair
                if (pair.second > 0) lastEnd = maxOf(lastEnd, pair.second)
            }
            return out
        }

        // 节次列不可用：按数据行序递增，一行一节
        var p = 0
        for (i in firstDataRow until n) {
            p++
            out[i] = p to p
        }
        return out
    }

    /** 供排错用的完整转储：原始表格 + 解析出来的事件。 */
    fun dump(rows: List<List<String>>, rowspans: List<List<Int>>): String {
        val sb = StringBuilder()
        sb.append(describe(rows)).append('\n')
        rows.forEachIndexed { i, row ->
            sb.append('r').append(i).append(": ")
            sb.append(row.joinToString("|") { it.replace('\n', '/') })
            if (i < rowspans.size) {
                sb.append("  spans=").append(rowspans[i].joinToString(","))
            }
            sb.append('\n')
        }
        val events = runCatching { parseTimetable(rows, rowspans) }.getOrElse { emptyList() }
        sb.append("--- parsed events (").append(events.size).append(") ---\n")
        events.forEach {
            sb.append('d').append(it.dayOfWeek).append(' ')
                .append(it.startPeriod).append('-').append(it.endPeriod)
                .append(" name=[").append(it.name).append(']')
                .append(" t=[").append(it.teacher).append(']')
                .append(" r=[").append(it.room).append(']')
                .append(" w=[").append(it.pattern.raw).append(']')
                .append('\n')
        }
        return sb.toString()
    }

    /** 布局诊断。解析结果异常时把它显示给用户，一眼就能看出是哪一步错了。 */
    fun describe(rows: List<List<String>>): String {
        if (rows.isEmpty()) return "空表格"
        val cleaned = rows.map { row -> row.map { normalizeCell(it) } }
        val columns = detectDayColumns(cleaned)
        val firstDataRow = if (columns.headerRow >= 0) columns.headerRow + 1 else 0
        val firstDayCol = columns.map.keys.minOrNull() ?: 0
        val periodColumn = if (firstDayCol > 0) firstDayCol - 1 else -1
        val rp = detectRowPeriods(cleaned, periodColumn, firstDataRow)
        val periods = rp.drop(firstDataRow).map { it.first }
        return buildString {
            append("行数=").append(rows.size)
            append(" 列数=").append(rows.firstOrNull()?.size ?: 0)
            append(" 表头行=").append(columns.headerRow)
            append(" 星期列=").append(columns.map.keys.sorted())
            append(" 节次列=").append(periodColumn)
            append(" 检出节次=").append(periods.joinToString(","))
        }
    }

    /**
     * 解析节次标签。
     *
     * 强智的写法比想象中杂，**逗号分隔是最容易踩坑的一种**：
     * ```
     * 第1,2节      ← 青农大实际用的就是这种，含义是 1-2 节
     * 第3,4节
     * 第10,11节
     * 第5节
     * 第1-2节 / 第1～2节 / 第1至2节 / 第1、2节
     * ```
     * 早期版本只认减号，于是 `第1,2节` 被 `(\d{1,2})节` 匹配成了「2」，
     * 一门两节的课被压成单节 —— 这就是"两节压成一节"的真正原因。
     *
     * 现在的做法：取「节」字之前的**所有**数字，返回 (min, max)。
     */
    private fun parsePeriod(text: String): Pair<Int, Int>? {
        if (text.isBlank()) return null
        val t = text
            .replace('（', '(').replace('）', ')')
            .replace('，', ',').replace('、', ',')
            .replace('－', '-').replace('—', '-').replace('–', '-').replace('～', '-')
        // 必须出现「节」字，否则可能把周次区间当成节次
        if (!t.contains('节')) return null
        val nums = ANY_NUMBER.findAll(t.substringBefore('节'))
            .mapNotNull { it.value.toIntOrNull() }
            .filter { it in 1..MAX_PERIOD }
            .toList()
        if (nums.isEmpty()) return null
        return nums.min() to nums.max()
    }

    // ------------------------------------------------------- 单元格 -> 课程条目

    data class CellEntry(
        val name: String,
        val teacher: String = "",
        val room: String = "",
        val weeks: WeekPattern = WeekPattern.ALWAYS,
        val startPeriod: Int? = null,
        val endPeriod: Int? = null,
    )

    /** 一个格子里可能塞了多门课，用横线分隔。 */
    fun parseCellEntries(cellText: String): List<CellEntry> {
        val lines = cellText.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val blocks = ArrayList<MutableList<String>>()
        var current = ArrayList<String>()
        for (line in lines) {
            if (DASH_LINE.matches(line)) {
                if (current.isNotEmpty()) {
                    blocks += current
                    current = ArrayList()
                }
            } else {
                current.add(line)
            }
        }
        if (current.isNotEmpty()) blocks += current

        return blocks.mapNotNull { parseBlock(it) }
    }

    private fun parseBlock(lines: List<String>): CellEntry? {
        val keyed = LinkedHashMap<String, String>()
        val anonymous = ArrayList<String>()

        for (line in lines) {
            val sep = line.indexOf(TITLE_SEP)
            if (sep > 0) {
                val key = line.substring(0, sep).trim()
                val value = line.substring(sep + 1).replace(TITLE_SEP, ' ').trim()
                if (key.isNotEmpty() && value.isNotEmpty()) keyed[key] = value
                else anonymous.add(value)
            } else {
                anonymous.add(line)
            }
        }

        fun findKeyed(vararg needles: String): String =
            keyed.entries.firstOrNull { e -> needles.any { e.key.contains(it) } }?.value.orEmpty()

        // 单元格里可能带 "第1节 / 1-2节" 这样的行：它既不是课程名也不是教师，
        // 但可以用来校正节次。先摘出来，免得污染教师/教室的启发式判断。
        var inlinePeriod: Pair<Int, Int>? = null
        run {
            val kept = ArrayList<String>(anonymous.size)
            for (line in anonymous) {
                val t = line.trim()
                if (PERIOD_ONLY_LINE.matches(t)) {
                    if (inlinePeriod == null) inlinePeriod = parsePeriod(t)
                } else {
                    kept.add(line)
                }
            }
            anonymous.clear()
            anonymous.addAll(kept)
        }

        var weeksText = findKeyed("周次", "周")
        if (weeksText.isEmpty()) {
            // 没有 title 就在普通行里找。周次有两种形态：
            //   ① 整行就是周次，例如 "1-16周"
            //   ② 内嵌在课程名里，例如 "大学物理(1-12周)"
            // ② 必须**就地把周次抠掉**，不能整行删除，否则课程名会被吃掉。
            val idx = anonymous.indexOfFirst { WEEK_HINT.containsMatchIn(it) }
            if (idx >= 0) {
                val line = anonymous[idx]
                WEEK_HINT.find(line)?.let { m ->
                    weeksText = m.value
                    val stripped = line.replace(m.value, "")
                        .replace(Regex("""[（(]\s*[)）]"""), "")
                        .trim()
                    if (stripped.isEmpty()) anonymous.removeAt(idx) else anonymous[idx] = stripped
                }
            }
        } else if (anonymous.isNotEmpty()) {
            // 周次来自 title，但课程名里可能还带了一份，一并去掉
            val first = anonymous[0]
            WEEK_HINT.find(first)?.let { m ->
                val stripped = first.replace(m.value, "")
                    .replace(Regex("""[（(]\s*[)）]"""), "")
                    .trim()
                if (stripped.isNotEmpty()) anonymous[0] = stripped
            }
        }

        // 只认「含节、且不含周」的 title —— 强智的周次 title 常写成
        // "周次(节次)"，里面也含"节次"，直接用 findKeyed 会误取周次值。
        val periodText = keyed.entries
            .firstOrNull { e -> e.key.contains("节") && !e.key.contains("周") }
            ?.value.orEmpty()
        val pr = parsePeriod(periodText) ?: inlinePeriod

        val name = anonymous.removeFirstOrNull()?.trim().orEmpty()
        if (name.isEmpty()) return null

        var teacher = findKeyed("老师", "教师", "任课", "师")
        var room = findKeyed("教室", "地点", "上课地", "室")

        val rest = anonymous.filter { it.isNotBlank() }
        when {
            teacher.isEmpty() && room.isEmpty() && rest.size >= 2 -> {
                val roomIdx = rest.indexOfFirst { looksLikeRoom(it) }
                if (roomIdx >= 0) {
                    room = rest[roomIdx]
                    teacher = rest.firstOrNull { it != room }.orEmpty()
                } else {
                    teacher = rest[0]
                    room = rest.getOrElse(1) { "" }
                }
            }
            teacher.isEmpty() && room.isEmpty() && rest.size == 1 -> {
                if (looksLikeRoom(rest[0])) room = rest[0] else teacher = rest[0]
            }
            teacher.isEmpty() && rest.isNotEmpty() -> teacher = rest[0]
            room.isEmpty() && rest.isNotEmpty() -> room = rest.last()
        }

        return CellEntry(
            name = name,
            teacher = teacher,
            room = room,
            weeks = WeekPatternParser.parse(weeksText),
            startPeriod = pr?.first,
            endPeriod = pr?.second,
        )
    }

    /** 教室通常带数字或含这些字；教师名通常是 2-5 个汉字且不含数字。 */
    fun looksLikeRoom(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty()) return false
        if (t.any { it.isDigit() }) return true
        if (listOf("楼", "教室", "馆", "场", "厅", "阶", "机房", "实验室", "中心").any { t.contains(it) }) return true
        return false
    }

    fun looksLikeTeacher(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty() || t.length > 6) return false
        if (t.any { it.isDigit() }) return false
        return t.all { it.code in 0x4E00..0x9FFF || it == '·' || it == ' ' }
    }

    /** 单元格里的换行/空白统一化。 */
    fun normalizeCell(raw: String): String =
        raw.replace('\r', '\n')
            .replace(Regex("""\n{2,}"""), "\n")
            .split('\n')
            .joinToString("\n") { it.trim() }
            .trim()

    // ------------------------------------------------------ 考试 / 成绩 / 空教室

    private fun headerIndexOf(headers: List<String>, vararg needles: String): Int =
        headers.indexOfFirst { h -> needles.any { h.contains(it) } }

    /**
     * 找「课程名称」列。
     *
     * 强智的成绩表里**同时有「课程编号」和「课程名称」**，两者都含"课程"。
     * 早期实现直接用 `headerIndexOf(headers, "课程")`，取到的是**先出现的课程编号**，
     * 于是成绩页显示的全是 `4040001` 这种编号 —— 这就是那个 bug 的根因。
     */
    private fun courseNameColumn(headers: List<String>): Int {
        val direct = headerIndexOf(headers, "课程名称", "科目名称", "课程名")
        if (direct >= 0) return direct
        // 退而求其次：含"课程/科目"但明确不是编号类的列
        return headers.indexOfFirst { h ->
            (h.contains("课程") || h.contains("科目")) &&
                !h.contains("编号") && !h.contains("代码") && !h.contains("编码")
        }
    }

    fun parseExams(rows: List<List<String>>): List<ExamItem> {
        if (rows.size < 2) return emptyList()
        val headers = rows[0].map { it.trim() }
        val iCourse = courseNameColumn(headers)
        val iDate = headerIndexOf(headers, "日期", "时间", "考试时间")
        val iRoom = headerIndexOf(headers, "考场", "地点", "教室")
        val iSeat = headerIndexOf(headers, "座位")
        if (iCourse < 0 && iDate < 0) return emptyList()

        return rows.drop(1).mapNotNull { r ->
            val course = r.getOrNull(iCourse).orEmpty().trim()
            if (course.isEmpty() && iCourse >= 0) return@mapNotNull null
            val dateTime = r.getOrNull(iDate).orEmpty().trim()
            ExamItem(
                course = course,
                date = extractDate(dateTime),
                time = extractTimeRange(dateTime),
                room = r.getOrNull(iRoom).orEmpty().trim(),
                seat = r.getOrNull(iSeat).orEmpty().trim(),
            )
        }.filter { it.course.isNotEmpty() }
    }

    fun parseGrades(rows: List<List<String>>): List<GradeItem> {
        if (rows.size < 2) return emptyList()
        val headers = rows[0].map { it.trim() }
        // 必须取「课程名称」，不能取到「课程编号」
        val iCourse = courseNameColumn(headers)
        val iTerm = headerIndexOf(headers, "开课学期", "学期")
        val iCredit = headerIndexOf(headers, "学分")
        val iScore = headerIndexOf(headers, "成绩", "分数", "总评")
        val iGpa = headerIndexOf(headers, "绩点")
        val iKind = headerIndexOf(headers, "性质", "类别", "类型")
        if (iCourse < 0) return emptyList()

        return rows.drop(1).mapNotNull { r ->
            val course = r.getOrNull(iCourse).orEmpty().trim()
            if (course.isEmpty()) return@mapNotNull null
            GradeItem(
                term = r.getOrNull(iTerm).orEmpty().trim(),
                course = course,
                credit = r.getOrNull(iCredit).orEmpty().trim().toDoubleOrNull() ?: 0.0,
                score = r.getOrNull(iScore).orEmpty().trim().toDoubleOrNull() ?: 0.0,
                gpa = r.getOrNull(iGpa).orEmpty().trim().toDoubleOrNull() ?: 0.0,
                kind = r.getOrNull(iKind).orEmpty().trim(),
            )
        }
    }

    fun parseClassrooms(rows: List<List<String>>): List<ClassroomItem> {
        if (rows.isEmpty()) return emptyList()
        val headers = rows.first().map { it.trim() }
        val hasHeader = headers.any { it.contains("教室") || it.contains("校区") }
        val data = if (hasHeader) rows.drop(1) else rows

        val iRoom = if (hasHeader) headerIndexOf(headers, "教室", "房间") else 0
        val iBuilding = if (hasHeader) headerIndexOf(headers, "楼", "教学楼", "建筑") else -1
        val iCampus = if (hasHeader) headerIndexOf(headers, "校区") else -1
        val iFree = if (hasHeader) headerIndexOf(headers, "空闲", "节次") else -1
        val iCap = if (hasHeader) headerIndexOf(headers, "容纳", "人数", "座位") else -1

        return data.mapNotNull { r ->
            val room = r.getOrNull(if (iRoom >= 0) iRoom else 0).orEmpty().trim()
            if (room.isEmpty()) return@mapNotNull null
            ClassroomItem(
                room = room,
                building = if (iBuilding >= 0) r.getOrNull(iBuilding).orEmpty().trim() else "",
                campus = if (iCampus >= 0) r.getOrNull(iCampus).orEmpty().trim() else "",
                freeSlots = if (iFree >= 0) r.getOrNull(iFree).orEmpty().trim() else "",
                capacity = if (iCap >= 0) r.getOrNull(iCap).orEmpty().filter { it.isDigit() }.toIntOrNull() ?: 0 else 0,
            )
        }
    }

    // ------------------------------------------------------------------ 小工具

    internal val DATE_RE = Regex("""(\d{4})\s*[-/年.]\s*(\d{1,2})\s*[-/月.]\s*(\d{1,2})""")
    internal val TIME_RE = Regex("""(\d{1,2}:\d{2})\s*[-~～—–至]\s*(\d{1,2}:\d{2})""")

    fun extractDate(text: String): String {
        val m = DATE_RE.find(text) ?: return ""
        val y = m.groupValues[1]
        val mo = m.groupValues[2].padStart(2, '0')
        val d = m.groupValues[3].padStart(2, '0')
        return "$y-$mo-$d"
    }

    fun extractTimeRange(text: String): String {
        val m = TIME_RE.find(text) ?: return ""
        return "${m.groupValues[1]}-${m.groupValues[2]}"
    }
}
