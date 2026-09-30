package cn.edu.qau.timetable.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.edu.qau.timetable.core.SilenceMode

/**
 * 上课静音的闹钟落点：开始静音 / 还原铃声。
 *
 * 和 [ReminderReceiver] 一样用显式 Intent + 不导出的 Receiver
 * （manifest 里没有 intent-filter），所以不需要额外的 action 来区分 ——
 * 靠 extra 分辨即可。这样取消闹钟时只要 requestCode 对得上
 * 就能用 `Intent(context, SilenceReceiver::class.java)` 找回来。
 */
class SilenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getStringExtra(EXTRA_KIND)) {
            KIND_RESTORE -> RingerModeController.restore(context)
            else -> RingerModeController.silence(
                context,
                SilenceMode.fromName(intent.getStringExtra(EXTRA_MODE)),
            )
        }
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_MODE = "mode"

        private const val KIND_SILENCE = "silence"
        private const val KIND_RESTORE = "restore"

        fun intentFor(context: Context, restore: Boolean, mode: SilenceMode): Intent =
            Intent(context, SilenceReceiver::class.java).apply {
                putExtra(EXTRA_KIND, if (restore) KIND_RESTORE else KIND_SILENCE)
                putExtra(EXTRA_MODE, mode.name)
            }
    }
}
