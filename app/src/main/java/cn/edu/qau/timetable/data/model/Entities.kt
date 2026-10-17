package cn.edu.qau.timetable.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.Term
import cn.edu.qau.timetable.core.WeekPattern
import cn.edu.qau.timetable.core.WeekPatternParser
import cn.edu.qau.timetable.core.kaoyan.SchoolItem
import cn.edu.qau.timetable.domain.CourseEvent
import java.time.LocalDate

@Entity(tableName = "terms")
data class TermEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 形如 2026-2027-1 */
    val name: String,
    /** 第 1 周周一，ISO yyyy-MM-dd */
    val startDate: String,
    val totalWeeks: Int = Term.DEFAULT_WEEKS,
    val campus: String = Campus.CHENGYANG.name,
    val active: Boolean = false,
) {
    /** 第 1 周周一；没填或格式不对时返回 null。 */
    val startMondayOrNull: LocalDate?
        get() = runCatching { LocalDate.parse(startDate) }.getOrNull()

    /**
     * 转成领域对象；**算不出第 1 周周一就返回 null**。
     *
     * 以前这里解析失败时兜底成 `LocalDate.now()`，等于说「今天就是第 1 周」——
     * 周次换算会一直显示「第 1 周」，比报错更难发现。
     * 宁可把 null 往上传，让 UI 老实说「推算不出第几周」。
     */
    fun toDomain(): Term? {
        val start = startMondayOrNull ?: return null
        return Term(
            name = name,
            startMonday = start,
            totalWeeks = totalWeeks,
            campus = Campus.fromName(campus),
        )
    }
}

@Entity(tableName = "courses", indices = [Index("termId")])
data class CourseEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val termId: Long,
    val name: String,
    val teacher: String = "",
    val room: String = "",
    /** 1 = 周一 … 7 = 周日 */
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    /** 原始周次文本，例如 "1-16周(单)" */
    val weekRaw: String = "",
    /** 解析后的周次，形如 "1,3,5,7" */
    val weeks: String = "",
    val colorSeed: Int = 0,
) {
    fun toDomain(): CourseEvent = CourseEvent(
        id = id,
        name = name,
        teacher = teacher,
        room = room,
        dayOfWeek = dayOfWeek,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        pattern = if (weeks.isBlank()) {
            WeekPatternParser.parse(weekRaw)
        } else {
            WeekPattern(weekRaw, WeekPattern.fromStorage(weeks))
        },
    )

    companion object {
        fun fromDomain(termId: Long, e: CourseEvent, colorSeed: Int = 0): CourseEventEntity =
            CourseEventEntity(
                termId = termId,
                name = e.name,
                teacher = e.teacher,
                room = e.room,
                dayOfWeek = e.dayOfWeek,
                startPeriod = e.startPeriod,
                endPeriod = e.endPeriod,
                weekRaw = e.pattern.raw,
                weeks = e.pattern.toStorage(),
                colorSeed = colorSeed,
            )
    }
}

@Entity(tableName = "exams", indices = [Index("termId")])
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val termId: Long,
    val course: String,
    /** yyyy-MM-dd */
    val date: String = "",
    /** 例如 14:00-16:00 */
    val time: String = "",
    val room: String = "",
    val seat: String = "",
    val note: String = "",
)

@Entity(tableName = "grades", indices = [Index("termId")])
data class GradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val termId: Long,
    val termName: String = "",
    val course: String,
    val credit: Double = 0.0,
    val score: Double = 0.0,
    val gpa: Double = 0.0,
    val kind: String = "",
)

// 原先这里还有一个 ClassroomEntity（tableName = "classrooms"），
// 用来存「空闲教室」查询结果。学校教务系统禁止访问空闲教室界面，
// 这个功能整条链路已下线，实体一并删除 —— 存留的旧表由
// AppDatabase.MIGRATION_1_2 显式 DROP，不影响课表 / 成绩 / 考试。

/**
 * 考研院校招生条目（院校 × 专业各一行）。
 *
 * 为什么不直接让 Room 存 `core.kaoyan.SchoolItem`：core 包是纯 Kotlin、
 * 零 Android 依赖，单测在 JVM 上直接跑。一旦给它挂上 `@Entity`，
 * 推荐引擎和站点目录的单测就得连带 JVM 里的 Room 运行时 ——
 * 为了少写这二十行映射，把纯逻辑层弄脏不划算。
 *
 * 索引只建在 `majorCode` 上：选校页按专业筛，而 `tier`/`name` 只是排序，
 * 全表最多几十上百行，再建索引纯属浪费写入。
 */
@Entity(tableName = "kaoyan_schools", indices = [Index("majorCode")])
data class KaoyanSchoolEntity(
    /** 稳定主键，形如 "10423-0812"，院校代码 + 学科代码。 */
    @PrimaryKey val id: String,
    val name: String,
    val province: String,
    val city: String,
    /** 院校层次原文，如 "985"；打分时由 SchoolTier.fromName 解析。 */
    val tier: String,
    val majorCode: String,
    val majorName: String,
    val scoreLast: Int = 0,
    val scorePrev: Int = 0,
    val quota: Int = 0,
    val applicants: Int = 0,
    val mathRequired: Boolean = false,
    val tags: String = "",
    val note: String = "",
) {
    fun toDomain(): SchoolItem = SchoolItem(
        id = id,
        name = name,
        province = province,
        city = city,
        tier = tier,
        majorCode = majorCode,
        majorName = majorName,
        scoreLast = scoreLast,
        scorePrev = scorePrev,
        quota = quota,
        applicants = applicants,
        mathRequired = mathRequired,
        tags = tags,
        note = note,
    )

    companion object {
        fun fromDomain(e: SchoolItem): KaoyanSchoolEntity = KaoyanSchoolEntity(
            id = e.id,
            name = e.name,
            province = e.province,
            city = e.city,
            tier = e.tier,
            majorCode = e.majorCode,
            majorName = e.majorName,
            scoreLast = e.scoreLast,
            scorePrev = e.scorePrev,
            quota = e.quota,
            applicants = e.applicants,
            mathRequired = e.mathRequired,
            tags = e.tags,
            note = e.note,
        )
    }
}
