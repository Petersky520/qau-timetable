package cn.edu.qau.timetable.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志落盘。
 *
 * 本机没有 Android 设备/模拟器，很多问题无法复现，所以把未捕获异常的
 * 堆栈写到应用私有目录，App 下次启动后可在「设置 → 上次崩溃日志」里查看。
 *
 * 注意：WebView 的渲染进程是**独立进程**，它若崩溃不会走这里；
 * 那种情况需要 `adb logcat` 才能看到。
 */
object CrashLogger {

    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(app, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val text = buildString {
            append("时间：").append(time).append('\n')
            append("线程：").append(thread.name).append('\n')
            append("设备：").append(android.os.Build.MANUFACTURER).append(' ')
                .append(android.os.Build.MODEL).append('\n')
            append("系统：Android ").append(android.os.Build.VERSION.RELEASE)
                .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n\n")
            append(sw.toString())
        }
        runCatching { File(context.filesDir, FILE_NAME).writeText(text) }
    }

    fun read(context: Context): String? =
        File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}
