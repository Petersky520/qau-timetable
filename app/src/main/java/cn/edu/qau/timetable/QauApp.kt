package cn.edu.qau.timetable

import android.app.Application
import android.content.Context
import androidx.room.Room
import cn.edu.qau.timetable.data.db.AppDatabase
import cn.edu.qau.timetable.data.prefs.SettingsStore
import cn.edu.qau.timetable.data.repo.KaoyanRepository
import cn.edu.qau.timetable.data.repo.TimetableRepository
import cn.edu.qau.timetable.notify.ReminderScheduler
import cn.edu.qau.timetable.notify.SilenceScheduler
import cn.edu.qau.timetable.util.CrashLogger

class QauApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        container = AppContainer(this)
    }
}

/** 极简手工依赖容器 —— 这个规模的 App 引入 Hilt 不划算。 */
class AppContainer(context: Context) {

    val db: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "qau_timetable.db",
    )
        // v1 -> v2 只 DROP 掉已下线的 `classrooms` 表，课表/成绩/考试原地保留。
        // v2 -> v3 建考研院校目录表。
        // 这两条迁移都必须先于 fallbackToDestructiveMigration 命中，
        // 否则升级等于清库。
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
        .fallbackToDestructiveMigration()
        .build()

    val settings: SettingsStore = SettingsStore(context.applicationContext)

    val repo: TimetableRepository = TimetableRepository(context.applicationContext, db, settings)

    /** 考研模块：院校目录、AI 搜索、收藏。 */
    val kaoyan: KaoyanRepository = KaoyanRepository(context.applicationContext, db, settings)

    val reminders: ReminderScheduler = ReminderScheduler(context.applicationContext, repo)

    /** 上课自动静音（依赖「通知策略访问权限」）。 */
    val silence: SilenceScheduler = SilenceScheduler(context.applicationContext, repo)
}
