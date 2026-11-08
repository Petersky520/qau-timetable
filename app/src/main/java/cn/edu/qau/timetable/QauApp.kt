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

/**
 * 极简手工依赖容器 —— 这个规模的 App 引入 Hilt 不划算。
 *
 * ## 为什么这么多成员是 `by lazy`（v3.6 启动优化）
 *
 * `onCreate` 里做的事全部算进**冷启动时间**，而用户对冷启动最敏感：
 * 从点图标到看见课表，这段时间里做的每一件与「画第一屏」无关的事，
 * 都是在让用户干等。
 *
 * 改造前这个容器有 6 个 val 在 `onCreate` 里同步构造：
 * Room 建库（要读 schema、跑迁移）、DataStore、两个 Repository、
 * 两个 Scheduler。其中：
 *
 *   - `db` 要打开数据库文件，**最重的一项**；
 *   - `reminders` / `silence` 各自起一个 `CoroutineScope`，但它们的
 *     `reschedule()` 要等 ViewModel 起来才调 —— 构造本身是白等的；
 *   - `kaoyan` 只在进考研页时才用得到（Lite 版甚至完全没有）。
 *
 * 改成 `by lazy` 之后，只有真正访问时才初始化。线程安全由 Kotlin 的
 * lazy 默认（SYNCHRONIZED）保证，多个线程首次同时访问不会重复构造。
 *
 * ⚠️ 注意：`lazy` 只推迟**构造**，不改变「谁在什么时候调 `reschedule()`」。
 * 两个 Scheduler 的首次排程仍由 MainViewModel / BootReceiver 触发，
 * 所以启动优化不会让提醒延后生效。
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val db: AppDatabase by lazy {
        Room.databaseBuilder(
            appContext,
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
    }

    val settings: SettingsStore by lazy { SettingsStore(appContext) }

    val repo: TimetableRepository by lazy {
        TimetableRepository(appContext, db, settings)
    }

    /** 考研模块：院校目录、AI 搜索、收藏。Lite 版不会用到它。 */
    val kaoyan: KaoyanRepository by lazy {
        KaoyanRepository(appContext, db, settings)
    }

    val reminders: ReminderScheduler by lazy {
        ReminderScheduler(appContext, repo)
    }

    /** 上课自动静音（依赖「通知策略访问权限」）。 */
    val silence: SilenceScheduler by lazy {
        SilenceScheduler(appContext, repo)
    }
}
