package cn.edu.qau.timetable

import android.app.Application
import android.content.Context
import androidx.room.Room
import cn.edu.qau.timetable.data.db.AppDatabase
import cn.edu.qau.timetable.data.prefs.SettingsStore
import cn.edu.qau.timetable.data.repo.TimetableRepository
import cn.edu.qau.timetable.notify.ReminderScheduler
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
    ).fallbackToDestructiveMigration().build()

    val settings: SettingsStore = SettingsStore(context.applicationContext)

    val repo: TimetableRepository = TimetableRepository(context.applicationContext, db, settings)

    val reminders: ReminderScheduler = ReminderScheduler(context.applicationContext, repo)
}
