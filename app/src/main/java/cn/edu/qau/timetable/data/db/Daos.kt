package cn.edu.qau.timetable.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cn.edu.qau.timetable.data.model.CourseEventEntity
import cn.edu.qau.timetable.data.model.ExamEntity
import cn.edu.qau.timetable.data.model.GradeEntity
import cn.edu.qau.timetable.data.model.KaoyanSchoolEntity
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
interface KaoyanSchoolDao {
    @Query("SELECT * FROM kaoyan_schools ORDER BY tier, name")
    fun observeAll(): Flow<List<KaoyanSchoolEntity>>

    @Query("SELECT * FROM kaoyan_schools WHERE majorCode = :code ORDER BY tier, name")
    fun observeByMajor(code: String): Flow<List<KaoyanSchoolEntity>>

    /** 一次性读取。推荐引擎要的是完整列表，用 Flow 反而让 ViewModel 多做一层收集。 */
    @Query("SELECT * FROM kaoyan_schools ORDER BY tier, name")
    suspend fun allNow(): List<KaoyanSchoolEntity>

    @Query("SELECT COUNT(*) FROM kaoyan_schools")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<KaoyanSchoolEntity>)

    @Query("DELETE FROM kaoyan_schools")
    suspend fun clear()

    /**
     * 整体替换目录。
     *
     * 放在一个事务里：远端目录和本地目录的差异是"整份换掉"，
     * 中途失败留下半份旧半份新的目录，会让筛选结果忽多忽少。
     */
    @Transaction
    suspend fun replaceAll(items: List<KaoyanSchoolEntity>) {
        clear()
        insertAll(items)
    }
}

@Database(
    entities = [
        TermEntity::class,
        CourseEventEntity::class,
        ExamEntity::class,
        GradeEntity::class,
        KaoyanSchoolEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun termDao(): TermDao
    abstract fun courseDao(): CourseDao
    abstract fun examDao(): ExamDao
    abstract fun gradeDao(): GradeDao
    abstract fun kaoyanSchoolDao(): KaoyanSchoolDao

    companion object {
        /**
         * v1 -> v2：删掉 `classrooms` 表。
         *
         * v1 里还有「空闲教室」这张表，学校教务系统禁止访问空闲教室界面后
         * 这个功能整条链路都下线了，表也就没有意义。
         *
         * 关键点：**必须显式写这条迁移**。App 用的是
         * `fallbackToDestructiveMigration()`，不写的话升版本会把整个库删掉重建 ——
         * 用户的课表、成绩、考试安排全没了，只为了删一张没人用的表。
         * 这里只 DROP 那一张，其余三张原地不动。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `classrooms`")
            }
        }

        /**
         * v2 -> v3：新增考研院校目录表。
         *
         * 这条迁移只在 `Version 3.2` 首次启动时跑一次，把空表建出来，
         * 种子数据由 `KaoyanRepository.ensureSeeded()` 灌进去；
         * 迁移里不塞数据 —— 迁移必须只做结构变更，否则以后改种子还要再写一条迁移。
         *
         * ⚠️ 建表语句与 Room 生成的
         * `build/generated/ksp/debug/kotlin/.../AppDatabase_Impl.kt` 里
         * `createAllTables` 的那两条 **逐字一致**（含反引号、列顺序、NOT NULL 与默认值）。
         * 差一个字，Room 打开数据库时就会抛「Room cannot verify the data integrity」。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `kaoyan_schools` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `province` TEXT NOT NULL, `city` TEXT NOT NULL, `tier` TEXT NOT NULL, `majorCode` TEXT NOT NULL, `majorName` TEXT NOT NULL, `scoreLast` INTEGER NOT NULL, `scorePrev` INTEGER NOT NULL, `quota` INTEGER NOT NULL, `applicants` INTEGER NOT NULL, `mathRequired` INTEGER NOT NULL, `tags` TEXT NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_kaoyan_schools_majorCode` ON `kaoyan_schools` (`majorCode`)"
                )
            }
        }
    }
}
