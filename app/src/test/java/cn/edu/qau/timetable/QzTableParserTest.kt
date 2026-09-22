package cn.edu.qau.timetable

import cn.edu.qau.timetable.data.qz.QzTableParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QzTableParserTest {

    private val header = listOf("节次", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

    /** 构造一行：第 p 节，周一到周日的内容。 */
    private fun row(p: Int, vararg days: String): List<String> =
        listOf(p.toString()) + (0 until 7).map { days.getOrElse(it) { "" } }

    @Test
    fun `rowspan 合并的两节被还原成一门课`() {
        // JS 侧对 rowspan 做复制填充，所以连续两行文本相同
        val cell = "高等数学A\n张三\n1-16周\n教1-101"
        val rows = listOf(
            header,
            row(1, cell),
            row(2, cell),
            row(3, ""),
        )
        val events = QzTableParser.parseTimetable(rows)

        assertEquals(1, events.size)
        val e = events.first()
        assertEquals("高等数学A", e.name)
        assertEquals(1, e.dayOfWeek)
        assertEquals(1, e.startPeriod)
        assertEquals(2, e.endPeriod)
        assertEquals(1, e.pattern.weeks.min())
        assertEquals(16, e.pattern.weeks.max())
    }

    @Test
    fun `教师与教室的启发式区分`() {
        val cell = "大学英语\n李四\n1-16周(单)\n文经楼B203"
        val events = QzTableParser.parseTimetable(
            listOf(header, row(3, "", "", cell))
        )
        assertEquals(1, events.size)
        val e = events.first()
        assertEquals("大学英语", e.name)
        assertEquals("李四", e.teacher)
        assertEquals("文经楼B203", e.room)
        assertEquals(3, e.dayOfWeek)
        assertEquals(setOf(1, 3, 5, 7, 9, 11, 13, 15), e.pattern.weeks)
    }

    @Test
    fun `带 title 属性的行优先于启发式`() {
        val sep = QzTableParser.TITLE_SEP
        val cell = "线性代数\n" +
            "老师${sep}王五\n" +
            "周次${sep}2-10周(双)\n" +
            "教室${sep}教学楼A301"
        val events = QzTableParser.parseTimetable(
            listOf(header, row(2, "", cell))
        )
        assertEquals(1, events.size)
        val e = events.first()
        assertEquals("线性代数", e.name)
        assertEquals("王五", e.teacher)
        assertEquals("教学楼A301", e.room)
        assertEquals(setOf(2, 4, 6, 8, 10), e.pattern.weeks)
    }

    @Test
    fun `一个格子里塞两门课用横线分隔`() {
        val cell = "体育\n赵六\n1-8周\n操场\n" +
            "----------------------\n" +
            "形势与政策\n钱七\n9-16周\n教2-101"
        val events = QzTableParser.parseTimetable(
            listOf(header, row(5, "", "", "", "", cell))
        )
        assertEquals(2, events.size)
        assertEquals(setOf("体育", "形势与政策"), events.map { it.name }.toSet())
    }

    @Test
    fun `周次写在课程名括号里也能提取`() {
        val cell = "大学物理(1-12周)\n孙八\n综合楼101"
        val events = QzTableParser.parseTimetable(
            listOf(header, row(4, "", "", "", cell))
        )
        assertEquals(1, events.size)
        val e = events.first()
        assertEquals("大学物理", e.name)
        assertEquals((1..12).toSet(), e.pattern.weeks)
    }

    @Test
    fun `没有表头时按第1到7列兜底`() {
        val rows = listOf(
            listOf("1", "高数\n张老师\n1-16周\nA101", "", "", "", "", "", ""),
        )
        val events = QzTableParser.parseTimetable(rows)
        assertEquals(1, events.size)
        assertEquals(1, events.first().dayOfWeek)
    }

    @Test
    fun `第几节 写在首列也能识别`() {
        val rows = listOf(
            header,
            listOf("第3-4节", "英语\n王老师\n1-16周\nB202", "", "", "", "", "", ""),
        )
        val events = QzTableParser.parseTimetable(rows)
        assertEquals(1, events.size)
        assertEquals(3, events.first().startPeriod)
        assertEquals(4, events.first().endPeriod)
    }

    @Test
    fun `跨节的两格只差节次时仍算一门课`() {
        // 强智可能在跨节的每一格里都写节次，导致原始文本不同 ——
        // 这正是"导入后两节变一节"的根因，比较前必须归一化。
        val c1 = "高等数学A\n第1节\n张三\n1-16周\n教1-101"
        val c2 = "高等数学A\n第2节\n张三\n1-16周\n教1-101"
        val events = QzTableParser.parseTimetable(
            listOf(header, row(1, c1), row(2, c2), row(3, ""))
        )
        assertEquals(1, events.size)
        val e = events.first()
        assertEquals("高等数学A", e.name)
        assertEquals(1, e.startPeriod)
        assertEquals(2, e.endPeriod)
        assertEquals("张三", e.teacher)
        assertEquals("教1-101", e.room)
    }

    @Test
    fun `rowspan 是跨节的权威依据`() {
        val cell = "有机化学实验\n李四\n1-8周\n实验楼B101"
        val rows = listOf(
            header,
            row(6, cell), row(7, cell), row(8, cell), row(9, cell),
        )
        // 第 6 行是锚点：rowspan=4；7~9 行是延续（0）
        val spans = listOf(
            List(8) { 1 },
            listOf(1, 4, 0, 0, 0, 0, 0, 0),
            listOf(1, 0, 0, 0, 0, 0, 0, 0),
            listOf(1, 0, 0, 0, 0, 0, 0, 0),
            listOf(1, 0, 0, 0, 0, 0, 0, 0),
        )
        val events = QzTableParser.parseTimetable(rows, spans)
        assertEquals(1, events.size)
        assertEquals(6, events.first().startPeriod)
        assertEquals(9, events.first().endPeriod)
        assertEquals("有机化学实验", events.first().name)
    }

    @Test
    fun `单元格里的节次行不会被当成教室`() {
        val entries = QzTableParser.parseCellEntries("大学英语\n第3节\n王五\n1-16周\n文经楼B203")
        assertEquals(1, entries.size)
        assertEquals("大学英语", entries[0].name)
        assertEquals("王五", entries[0].teacher)
        assertEquals("文经楼B203", entries[0].room)
        assertEquals(3, entries[0].startPeriod)
    }

    @Test
    fun `首列不是节次列时不能崩（复现 coerceIn 空区间）`() {
        // 原 bug：
        //   节次列读不出来 -> 按行号兜底递增 -> 涨到 12
        //   -> endPeriod.coerceIn(12, 11) 抛
        //      IllegalArgumentException: Cannot coerce value to an empty range
        val rows = ArrayList<List<String>>()
        // 关键：7 列全是星期，没有「节次」列
        rows += listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
        for (i in 1..12) {
            rows += listOf("课程$i\n老师\n1-16周\nA10$i", "", "", "", "", "", "")
        }

        val events = QzTableParser.parseTimetable(rows)

        assertEquals(12, events.size)
        assertEquals((1..12).toList(), events.map { it.startPeriod })
        assertTrue(events.all { it.endPeriod >= it.startPeriod })
    }

    @Test
    fun `节次超过 11 节也不会崩`() {
        // 学校若真有 12 节，作息表只有 11 条，也不能抛异常
        val rows = ArrayList<List<String>>()
        rows += listOf("节次", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
        for (p in 1..14) {
            rows += listOf("$p", "课程$p\n老师\n1-16周\nA$p", "", "", "", "", "", "")
        }
        val events = QzTableParser.parseTimetable(rows)
        assertEquals(14, events.size)
        assertEquals(14, events.maxOf { it.endPeriod })
    }

    @Test
    fun `节次列在第一天左边一列时能被识别`() {
        // 节次列不是第 0 列（前面多了一列序号）
        val rows = listOf(
            listOf("序号", "节次", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"),
            listOf("1", "3", "英语\n王老师\n1-16周\nB202", "", "", "", "", "", ""),
        )
        val events = QzTableParser.parseTimetable(rows)
        assertEquals(1, events.size)
        assertEquals(3, events.first().startPeriod)
        assertEquals(3, events.first().endPeriod)
    }

    @Test
    fun `describe 能报出布局供排错`() {
        val rows = ArrayList<List<String>>()
        rows += listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
        for (i in 1..3) rows += listOf("课程$i\n老师\n1-16周\nA$i", "", "", "", "", "", "")
        val d = QzTableParser.describe(rows)
        assertTrue(d.contains("节次列=-1"))
        assertTrue(d.contains("行数=4"))
    }

    @Test
    fun `span=1 的相邻同文本格子仍要合并（复现 两节压成一节）`() {
        // 强智经常不写 rowspan，而是把同一门课在相邻格里重复写。
        // 上一版看到 span==1 就直接当单节，于是 1-2 节的课只剩第 2 节
        // —— 正是"我写的课表把两节压成一节"的原因。
        val cell = "概率论与数理统计\n王敏\n1-16周\n城阳B202"
        val rows = listOf(
            header,
            row(1, cell),
            row(2, cell),
            row(3, ""),
        )
        val spans = listOf(
            List(8) { 0 },
            listOf(0, 1, 1, 1, 1, 1, 1, 1),
            listOf(0, 1, 1, 1, 1, 1, 1, 1),
            listOf(0, 0, 0, 0, 0, 0, 0, 0),
        )
        val events = QzTableParser.parseTimetable(rows, spans)
        assertEquals(1, events.size)
        assertEquals("概率论与数理统计", events[0].name)
        assertEquals(1, events[0].startPeriod)
        assertEquals(2, events[0].endPeriod)
        assertEquals("城阳B202", events[0].room)
    }

    @Test
    fun `四节的实验课不会裂成两块`() {
        // 学校企业微信里"有机化学实验"是 1-4 节一整块
        val cell = "有机化学实验\n王辉\n1-8周\n化学楼331"
        val rows = listOf(
            header,
            row(1, cell), row(2, cell), row(3, cell), row(4, cell), row(5, ""),
        )
        val spans = List(6) { i -> List(8) { c -> if (i in 1..4 && c >= 1) 1 else 0 } }
        val events = QzTableParser.parseTimetable(rows, spans)
        assertEquals(1, events.size)
        assertEquals(1, events[0].startPeriod)
        assertEquals(4, events[0].endPeriod)
    }

    @Test
    fun `表头没识别出来时用 节次列出现1 定位起始行`() {
        // 星期名不是"星期一/周一"这类可识别写法 → headerRow=-1。
        // 若不修正起始行，整张表会串一位，所有课都往下错一节。
        val rows = listOf(
            listOf("", "一", "二", "三", "四", "五", "六", "日"),
            listOf("1", "高数\n张三\n1-16周\nA101", "", "", "", "", "", ""),
            listOf("2", "高数\n张三\n1-16周\nA101", "", "", "", "", "", ""),
            listOf("3", "", "", "", "", "", "", ""),
        )
        val events = QzTableParser.parseTimetable(rows)
        assertEquals(1, events.size)
        assertEquals(1, events[0].startPeriod)
        assertEquals(2, events[0].endPeriod)
    }

    @Test
    fun `真实青农大课表：逗号写法 第1,2节 必须解析成 1-2`() {
        // 数据来自用户抓取结果的原始 dump。
        // 关键：强智的节次标签是「第1,2节」这种逗号写法，不是「第1-2节」。
        // 早期版本只认减号，于是 (d{1,2})节 匹配到了「2」，整门课塌成单节。
        val sep = QzTableParser.TITLE_SEP
        fun cell(name: String, weeks: String, room: String, teacher: String = ""): String =
            buildString {
                append(name).append('\n')
                append("周次(节次)").append(sep).append(weeks).append('\n')
                append("(必修)").append('\n')
                append("教室").append(sep).append(room)
                if (teacher.isNotEmpty()) append('\n').append("老师").append(sep).append(teacher)
            }

        val lab = "有机化学实验"
        val labCell = cell(lab, "2-16(周)", "化学楼331", "曲")
        val hdr = listOf("", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
        val rows = listOf(
            hdr,
            listOf(
                "第1,2节",
                cell("概率论与数理统计", "1-11,13-16(周)", "城阳B202", "王敏"),
                cell("概率论与数理统计", "1-11,13-16(周)", "城阳B302", "王敏"),
                cell("大学英语Ⅲ", "1-17(周)", "城阳C103", "于永丽"),
                labCell, "", "", "",
            ),
            listOf(
                "第3,4节",
                cell("习近平新时代中国特色社会主义思想概论", "1-11,13-14(周)", "城阳A107"),
                cell("有机化学", "2-11,13-15(周)", "城阳A509", "王辉"),
                cell("体育Ⅲ", "1-18(周)", "城阳排球场b", "王晨秋"),
                labCell, "", "", "",
            ),
            listOf("第5节", "", "", "", "", "", "", ""),
            listOf(
                "第6,7节",
                cell("毛泽东思想和中国特色社会主义理论体系概论", "16(周)", "城阳A107"),
                cell("习近平新时代中国特色社会主义思想概论", "1-11,13-14(周)", "城阳A109"),
                cell("文献检索", "1-5,7-11,13-14(周)", "信息楼233(云)"),
                cell("仪器分析(全英文)", "1-7(周)", "城阳A407", "侯婷"),
                cell("有机化学", "2-11,13-15(周)", "城阳A405", "王辉"), "", "",
            ),
            listOf(
                "第8,9节",
                cell("形势与政策", "13(周)", "城阳A104", "赵丹"),
                cell("形势与政策", "13-15(周)", "城阳A208", "赵丹"),
                cell("毛泽东思想和中国特色社会主义理论体系概论", "1-11,13-17(周)", "城阳C105"),
                "", "", "", "",
            ),
            listOf("第10,11节", "", "", "", "", "", "", ""),
            listOf(
                "备注:",
                "4051001 仪器分析实验 制药2503 于专妮 1-18周;",
                "4051001 仪器分析实验 制药2503 于专妮 1-18周;",
                "4051001 仪器分析实验 制药2503 于专妮 1-18周;",
                "4051001 仪器分析实验 制药2503 于专妮 1-18周;",
                "4051001 仪器分析实验 制药2503 于专妮 1-18周;",
                "4051001 仪器分析实验 制药2503 于专妮 1-18周;",
                "4051001 仪器分析实验 制药2503 于专妮 1-18周;",
            ),
        )

        val events = QzTableParser.parseTimetable(rows)
        fun one(name: String, day: Int) = events.filter { it.name == name && it.dayOfWeek == day }

        // 逗号写法必须解析成区间
        one("概率论与数理统计", 1).single().let {
            assertEquals(1, it.startPeriod); assertEquals(2, it.endPeriod)
            assertEquals("王敏", it.teacher); assertEquals("城阳B202", it.room)
        }
        one("习近平新时代中国特色社会主义思想概论", 1).single().let {
            assertEquals(3, it.startPeriod); assertEquals(4, it.endPeriod)
        }
        one("有机化学", 2).single().let {
            assertEquals(3, it.startPeriod); assertEquals(4, it.endPeriod)
        }
        one("有机化学", 5).single().let {
            assertEquals(6, it.startPeriod); assertEquals(7, it.endPeriod)
        }
        one("仪器分析(全英文)", 4).single().let {
            assertEquals(6, it.startPeriod); assertEquals(7, it.endPeriod)
            assertEquals("侯婷", it.teacher); assertEquals("城阳A407", it.room)
        }

        // 第 5 节那行整行为空，不该产生课程
        assertTrue(events.none { it.startPeriod == 5 })

        // 有机化学实验横跨 1-4 节：两行文本相同 → 合并成一整块
        one("有机化学实验", 4).single().let {
            assertEquals(1, it.startPeriod); assertEquals(4, it.endPeriod)
        }

        // 「备注:」行不是课程行
        assertTrue(events.none { it.name.contains("4051001") })
        assertTrue(events.none { it.startPeriod >= 10 })
    }

    @Test
    fun `空表返回空`() {
        assertTrue(QzTableParser.parseTimetable(emptyList()).isEmpty())
        assertTrue(QzTableParser.parseTimetable(listOf(header)).isEmpty())
    }

    @Test
    fun `考试成绩解析`() {
        val rows = listOf(
            listOf("序号", "考试时间", "课程名称", "考场", "座位号"),
            listOf("1", "2027-01-05 14:00-16:00", "高等数学A", "教1-101", "23"),
        )
        val exams = QzTableParser.parseExams(rows)
        assertEquals(1, exams.size)
        assertEquals("高等数学A", exams[0].course)
        assertEquals("2027-01-05", exams[0].date)
        assertEquals("14:00-16:00", exams[0].time)
        assertEquals("教1-101", exams[0].room)
        assertEquals("23", exams[0].seat)
    }

    @Test
    fun `成绩表要取课程名称而不是课程编号`() {
        // 表头来自用户真实抓到的成绩页：
        // 序号|开课学期|课程编号|课程名称|成绩|绩点|学分|总学时|考核方式|课程属性|课程性质|考试性质
        val rows = listOf(
            listOf(
                "序号", "开课学期", "课程编号", "课程名称", "成绩", "绩点",
                "学分", "总学时", "考核方式", "课程属性", "课程性质", "考试性质",
            ),
            listOf(
                "1", "2025-2026-1", "4040001", "马克思主义基本原理", "76", "2.6",
                "3", "48", "考试", "必修", "通识课程", "正常考试",
            ),
            listOf(
                "2", "2025-2026-1", "4040002", "大学英语Ⅱ", "82", "3.2",
                "2.5", "40", "考试", "必修", "通识课程", "正常考试",
            ),
        )
        val grades = QzTableParser.parseGrades(rows)
        assertEquals(2, grades.size)
        // 关键断言：是课程名，不是 4040001
        assertEquals("马克思主义基本原理", grades[0].course)
        assertEquals("大学英语Ⅱ", grades[1].course)
        assertEquals("2025-2026-1", grades[0].term)
        assertEquals(3.0, grades[0].credit, 0.001)
        assertEquals(76.0, grades[0].score, 0.001)
        assertEquals(2.6, grades[0].gpa, 0.001)
        assertEquals("通识课程", grades[0].kind)
    }

    @Test
    fun `成绩解析`() {
        val rows = listOf(
            listOf("课程名称", "学分", "总成绩", "绩点", "课程性质"),
            listOf("高等数学A", "5.0", "88", "3.7", "必修"),
            listOf("大学英语", "3.0", "92", "4.0", "必修"),
        )
        val grades = QzTableParser.parseGrades(rows)
        assertEquals(2, grades.size)
        assertEquals(5.0, grades[0].credit, 0.001)
        assertEquals(88.0, grades[0].score, 0.001)
        assertEquals("必修", grades[0].kind)
    }

    @Test
    fun `教室识别与教师识别`() {
        assertTrue(QzTableParser.looksLikeRoom("教1-101"))
        assertTrue(QzTableParser.looksLikeRoom("文经楼B203"))
        assertTrue(QzTableParser.looksLikeRoom("实验楼"))
        assertTrue(QzTableParser.looksLikeTeacher("张三"))
        assertTrue(QzTableParser.looksLikeTeacher("欧阳娜娜"))
        assertTrue(!QzTableParser.looksLikeTeacher("教1-101"))
    }
}
