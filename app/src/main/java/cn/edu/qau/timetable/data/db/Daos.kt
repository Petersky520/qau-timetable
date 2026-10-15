package cn.edu.qau.timetable.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import cn.edu.qau.timetable.data.model.ClassroomEntity
import cn.edu.qau.timetable.data.model.CourseEventEntity
import cn.edu.qau.timetable.data.model.ExamEntity
import cn.edu.qau.timetable.data.model.GradeEntity
import cn.edu.qau.timetable.data.model.TermEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TermDao {
    @Query("SELECT * FROM terms ORDER BY id DESC")
    fun observeAll(): Flow<List<TermEntity>>

    @Query("SELECT * FROM terms WHERE active = 1 LIMIT 1")
    fun observeActive(): Flow<TermEntity?>

    @Query("SELECT * FROM terms WHERE active = 1 LIMIT 1")
    suspend fun active(): TermEntity?

    @Query("SELECT * FROM terms WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): TermEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(term: TermEntity): Long

    @Update
    suspend fun update(term: TermEntity)

    @Query("UPDATE terms SET active = 0")
    suspend fun clearActive()

    @Transaction
    suspend fun setActive(id: Long) {
        clearActive()
        setActiveFlag(id)
    }

    @Query("UPDATE terms SET active = 1 WHERE id = :id")
    suspend fun setActiveFlag(id: Long)
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE termId = :termId ORDER BY dayOfWeek, startPeriod")
    fun observeByTerm(termId: Long): Flow<List<CourseEventEntity>>

    @Query("SELECT * FROM courses WHERE termId = :termId ORDER BY dayOfWeek, startPeriod")
    suspend fun byTerm(termId: Long): List<CourseEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CourseEventEntity>)

    @Query("DELETE FROM courses WHERE termId = :termId")
    suspend fun deleteByTerm(termId: Long)

    @Transaction
    suspend fun replaceTerm(termId: Long, items: List<CourseEventEntity>) {
        deleteByTerm(termId)
        insertAll(items)
    }
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams WHERE termId = :termId ORDER BY date")
    fun observeByTerm(termId: Long): Flow<List<ExamEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExamEntity>)

    @Query("DELETE FROM exams WHERE termId = :termId")
    suspend fun deleteByTerm(termId: Long)

    @Transaction
    suspend fun replaceTerm(termId: Long, items: List<ExamEntity>) {
        deleteByTerm(termId)
        insertAll(items)
    }
}

@Dao
interface GradeDao {
    @Query("SELECT * FROM grades ORDER BY termName DESC, course")
    fun observeAll(): Flow<List<GradeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<GradeEntity>)

    @Query("DELETE FROM grades WHERE termName = :termName")
    suspend fun deleteByTermName(termName: String)

    @Query("DELETE FROM grades")
    suspend fun clear()
}

@Dao
interface ClassroomDao {
    @Query("SELECT * FROM classrooms ORDER BY building, room")
    fun observeAll(): Flow<List<ClassroomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ClassroomEntity>)

    @Query("DELETE FROM classrooms")
    suspend fun clear()
}

@Database(
    entities = [
        TermEntity::class,
        CourseEventEntity::class,
        ExamEntity::class,
        GradeEntity::class,
        ClassroomEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun termDao(): TermDao
    abstract fun courseDao(): CourseDao
    abstract fun examDao(): ExamDao
    abstract fun gradeDao(): GradeDao
    abstract fun classroomDao(): ClassroomDao
}
