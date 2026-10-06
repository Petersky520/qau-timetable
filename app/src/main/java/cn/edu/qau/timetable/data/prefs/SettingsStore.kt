package cn.edu.qau.timetable.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cn.edu.qau.timetable.core.Campus
import cn.edu.qau.timetable.core.SilenceMode
import cn.edu.qau.timetable.data.qz.QzEndpoints
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "qau_settings")

data class AppSettings(
    val campus: Campus = Campus.CHENGYANG,
    val termName: String = "",
    /** 第 1 周周一，ISO yyyy-MM-dd */
    val startMonday: String = "",
    val totalWeeks: Int = 20,
    val remindEnabled: Boolean = false,
    val remindMinutesBefore: Int = 15,
    /**
     * 上课自动静音。
     * 默认**关闭** —— 它会改动系统铃声，必须由用户主动开启，
     * 而且还需要用户去系统设置里授予「通知策略访问权限」。
     */
    val silenceEnabled: Boolean = false,
    /** 静音期间把铃声调成完全静音还是仅震动。 */
    val silenceMode: SilenceMode = SilenceMode.SILENT,
    /** Material You 动态取色（跟随手机壁纸）。 */
    val dynamicColor: Boolean = true,
    val kbUrl: String = QzEndpoints.TIMETABLE,
    val examUrl: String = QzEndpoints.EXAMS,
    val gradeUrl: String = QzEndpoints.GRADES,
    val classroomUrl: String = QzEndpoints.CLASSROOMS,
)

class SettingsStore(private val context: Context) {

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            campus = Campus.fromName(p[KEY_CAMPUS]),
            termName = p[KEY_TERM] ?: "",
            startMonday = p[KEY_START] ?: "",
            totalWeeks = p[KEY_WEEKS] ?: 20,
            remindEnabled = p[KEY_REMIND] ?: false,
            remindMinutesBefore = p[KEY_REMIND_MIN] ?: 15,
            silenceEnabled = p[KEY_SILENCE] ?: false,
            silenceMode = SilenceMode.fromName(p[KEY_SILENCE_MODE]),
            dynamicColor = p[KEY_DYNAMIC_COLOR] ?: true,
            kbUrl = p[KEY_KB_URL] ?: QzEndpoints.TIMETABLE,
            examUrl = p[KEY_EXAM_URL] ?: QzEndpoints.EXAMS,
            gradeUrl = p[KEY_GRADE_URL] ?: QzEndpoints.GRADES,
            classroomUrl = p[KEY_CLASSROOM_URL] ?: QzEndpoints.CLASSROOMS,
        )
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun setCampus(c: Campus) = edit { it[KEY_CAMPUS] = c.name }
    suspend fun setTerm(name: String, startMonday: String, totalWeeks: Int) = edit {
        it[KEY_TERM] = name
        it[KEY_START] = startMonday
        it[KEY_WEEKS] = totalWeeks
    }
    suspend fun setRemind(enabled: Boolean, minutesBefore: Int) = edit {
        it[KEY_REMIND] = enabled
        it[KEY_REMIND_MIN] = minutesBefore
    }

    suspend fun setSilence(enabled: Boolean, mode: SilenceMode) = edit {
        it[KEY_SILENCE] = enabled
        it[KEY_SILENCE_MODE] = mode.name
    }

    suspend fun setDynamicColor(enabled: Boolean) = edit {
        it[KEY_DYNAMIC_COLOR] = enabled
    }
    suspend fun setUrls(kb: String, exam: String, grade: String, classroom: String) = edit {
        it[KEY_KB_URL] = kb
        it[KEY_EXAM_URL] = exam
        it[KEY_GRADE_URL] = grade
        it[KEY_CLASSROOM_URL] = classroom
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val KEY_CAMPUS = stringPreferencesKey("campus")
        val KEY_TERM = stringPreferencesKey("term_name")
        val KEY_START = stringPreferencesKey("term_start")
        val KEY_WEEKS = intPreferencesKey("term_weeks")
        val KEY_REMIND = booleanPreferencesKey("remind_enabled")
        val KEY_REMIND_MIN = intPreferencesKey("remind_minutes")
        val KEY_SILENCE = booleanPreferencesKey("silence_enabled")
        val KEY_SILENCE_MODE = stringPreferencesKey("silence_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_KB_URL = stringPreferencesKey("url_kb")
        val KEY_EXAM_URL = stringPreferencesKey("url_exam")
        val KEY_GRADE_URL = stringPreferencesKey("url_grade")
        val KEY_CLASSROOM_URL = stringPreferencesKey("url_classroom")
    }
}
