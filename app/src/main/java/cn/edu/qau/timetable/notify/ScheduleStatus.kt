package cn.edu.qau.timetable.notify

import android.content.Context
import android.content.SharedPreferences

/**
 * 「上一次排程的结果」。
 *
 * ## 为什么需要这个东西
 *
 * 上课提醒和上课静音都有好几个早退条件，而且**每一个都是静默 return**：
 * 开关开着、界面看着正常，闹钟其实一个都没排上，用户什么都察觉不到，
 * 只能等发现「今天没提醒我」才回头怀疑。
 *
 * 排查这类问题时最难受的是：从外部完全看不出是
 *   ① 根本没排 ② 排了但没响 ③ 权限被系统拒了
 * 这三种情况的修法完全不同，但用户看到的都是「没提醒」。
 *
 * 所以每次排程结束都把结论落盘，设置页原样显示出来 ——
 * 用户自己就能看出是哪一环卡的，不用来回猜。
 *
 * ## 为什么用 SharedPreferences 而不是 DataStore
 *
 * 这是**诊断信息**，不是用户设置：写得频繁、只在设置页读一次、
 * 丢了也无所谓。DataStore 是异步 Flow，为它多开一条订阅不值当。
 */
internal object ScheduleStatus {

    enum class Kind(val label: String, val key: String) {
        REMINDER("上课提醒", "status_reminder"),
        SILENCE("上课静音", "status_silence"),
    }

    private const val PREF = "schedule_status"

    /** 写入一次排程结论。 */
    fun save(context: Context, kind: Kind, message: String) {
        prefs(context).edit().putString(kind.key, message).apply()
    }

    /** 读上一次的结论；从没排过时返回 null。 */
    fun read(context: Context, kind: Kind): String? =
        prefs(context).getString(kind.key, null)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
