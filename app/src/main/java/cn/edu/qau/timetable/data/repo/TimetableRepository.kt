package cn.edu.qau.timetable.data.repo

import android.content.Context
import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.Term
import cn.edu.qau.timetable.data.db.AppDatabase
import cn.edu.qau.timetable.data.model.ClassroomEntity
import cn.edu.qau.timetable.data.model.CourseEventEntity
import cn.edu.qau.timetable.data.model.ExamEntity
import cn.edu.qau.timetable.data.model.GradeEntity
import cn.edu.qau.timetable.data.model.TermEntity
import cn.edu.qau.timetable.data.prefs.AppSettings
import cn.edu.qau.timetable.data.prefs.SettingsStore
import cn.edu.qau.timetable.data.qz.QzPayload
import cn.edu.qau.timetable.data.qz.QzTableParser
import cn.edu.qau.timetable.domain.ClassroomItem
import cn.edu.qau.timetable.domain.CourseEvent
import cn.edu.qau.timetable.domain.ExamItem
import cn.edu.qau.timetable.domain.GradeItem
import cn.edu.qau.timetable.widget.TimetableWidgetProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * 数据仓库：把「抓取结果」写进 Room，把 Room 读出来给 UI。
 */
class TimetableRepository(
    private val context: Context,
    private val db: AppDatabase,
    val settings: SettingsStore,
) {

    val settingsFlow: Flow<AppSettings> = settings.flow

    fun observeTerms(): Flow<List<TermEntity>> = db.termDao().observeAll()
    fun observeActiveTerm(): Flow<TermEntity?> = db.termDao().observeActive()
    fun observeExams(termId: Long): Flow<List<ExamEntity>> = db.examDao().observeByTerm(termId)
    fun observeGrades(): Flow<List<GradeEntity>> = db.gradeDao().observeAll()
    fun observeClassrooms(): Flow<List<ClassroomEntity>> = db.classroomDao().observeAll()

    fun observeCourses(termId: Long): Flow<List<CourseEvent>> =
        db.courseDao().observeByTerm(termId).map { list -> list.map { it.toDomain() } }

    suspend fun coursesNow(termId: Long): List<CourseEvent> =
        db.courseDao().byTerm(termId).map { it.toDomain() }

    suspend fun activeTermEntity(): TermEntity? = db.termDao().active()

    suspend fun activeTerm(): Term? = db.termDao().active()?.toDomain()

    // ------------------------------------------------------------ 写入抓取结果

    /**
     * 把一次抓取写入数据库。返回写入条数，失败抛异常由调用方展示。
     */
    /**
     * 把一次抓取写入数据库。
     *
     * 这里**必须**把所有异常都吃下来变成 [IngestResult.message]：
     * 调用方是在 coroutine 里跑的，异常一旦抛出去就会直接杀掉进程
     * （这正是"抓取成功后闪退"的原因）。
     */
    suspend fun ingest(payload: QzPayload, current: AppSettings): IngestResult {
        if (!payload.ok) return IngestResult(0, payload.error.ifBlank { "抓取失败" })

        val result = try {
            when (payload.kind) {
                "kb" -> ingestTimetable(payload, current)
                "exam" -> ingestExams(payload, current)
                "grade" -> ingestGrades(payload, current)
                "classroom" -> ingestClassrooms(payload)
                else -> IngestResult(0, "未知的抓取类型：${payload.kind}")
            }
        } catch (t: Throwable) {
            IngestResult(
                0,
                "导入失败：${t.javaClass.simpleName}${t.message?.let { ": $it" } ?: ""}",
            )
        }

        // 数据变了，顺手把桌面小组件刷新掉（否则要等系统 30 分钟调度）
        if (result.ok) {
            runCatching { TimetableWidgetProvider.refresh(context) }
        }
        return result
    }

    private suspend fun ensureTerm(current: AppSettings): TermEntity {
        val name = current.termName.ifBlank { guessTermName() }
        val existing = db.termDao().byName(name)
        if (existing != null) return existing
        val start = current.startMonday.ifBlank {
            Term.guessStartMonday(name)?.toString() ?: LocalDate.now().toString()
        }
        val id = db.termDao().insert(
            TermEntity(
                name = name,
                startDate = start,
                totalWeeks = current.totalWeeks,
                campus = current.campus.name,
                active = true,
            )
        )
        db.termDao().setActive(id)
        return db.termDao().byName(name) ?: TermEntity(id = id, name = name, startDate = start)
    }

    private suspend fun ingestTimetable(p: QzPayload, current: AppSettings): IngestResult {
        val events = QzTableParser.parseTimetable(p.rows, p.rowspans)
        if (events.isEmpty()) {
            return IngestResult(0, "表格里没有解析出任何课程。可以在「同步」页查看抓到的原始表格，确认选对了页面。")
        }
        val term = ensureTerm(current)
        val entities = events.mapIndexed { idx, e ->
            CourseEventEntity.fromDomain(term.id, e, colorSeed = idx)
        }
        db.courseDao().replaceTerm(term.id, entities)
        return IngestResult(entities.size, "", termId = term.id)
    }

    private suspend fun ingestExams(p: QzPayload, current: AppSettings): IngestResult {
        val items: List<ExamItem> = QzTableParser.parseExams(p.rows)
        if (items.isEmpty()) return IngestResult(0, "没有解析出考试安排")
        val term = ensureTerm(current)
        db.examDao().replaceTerm(
            term.id,
            items.map {
                ExamEntity(
                    termId = term.id,
                    course = it.course,
                    date = it.date,
                    time = it.time,
                    room = it.room,
                    seat = it.seat,
                )
            }
        )
        return IngestResult(items.size, "", termId = term.id)
    }

    private suspend fun ingestGrades(p: QzPayload, current: AppSettings): IngestResult {
        val items: List<GradeItem> = QzTableParser.parseGrades(p.rows)
        if (items.isEmpty()) return IngestResult(0, "没有解析出成绩")
        val term = ensureTerm(current)

        // 按学期**先删后插**。之前只 insert 不 delete，
        // 每抓一次就多一份，52 门课里一大半是重复记录。
        items.map { it.term }.filter { it.isNotBlank() }.distinct()
            .forEach { db.gradeDao().deleteByTermName(it) }

        db.gradeDao().insertAll(
            items.map {
                GradeEntity(
                    termId = term.id,
                    termName = it.term.ifBlank { current.termName },
                    course = it.course,
                    credit = it.credit,
                    score = it.score,
                    gpa = it.gpa,
                    kind = it.kind,
                )
            }
        )
        return IngestResult(items.size, "")
    }

    private suspend fun ingestClassrooms(p: QzPayload): IngestResult {
        val items: List<ClassroomItem> = QzTableParser.parseClassrooms(p.rows)
        if (items.isEmpty()) return IngestResult(0, "没有解析出空闲教室")
        db.classroomDao().insertAll(
            items.map {
                ClassroomEntity(
                    building = it.building,
                    room = it.room,
                    campus = it.campus,
                    freeSlots = it.freeSlots,
                    capacity = it.capacity,
                )
            }
        )
        return IngestResult(items.size, "")
    }

    /** 没配置学期名时，按当前日期猜一个，例如 2026-2027-1。 */
    fun guessTermName(today: LocalDate = LocalDate.now()): String {
        val y = today.year
        val m = today.monthValue
        return if (m in 8..12 || m == 1) {
            val start = if (m == 1) y - 1 else y
            "$start-${start + 1}-1"
        } else {
            val start = y - 1
            "$start-${start + 1}-2"
        }
    }

    data class IngestResult(
        val count: Int,
        val message: String = "",
        val termId: Long = -1,
    ) {
        val ok: Boolean get() = message.isEmpty()
    }
}
