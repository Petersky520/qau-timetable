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
import cn.edu.qau.timetable.core.DayOverride
import cn.edu.qau.timetable.core.DayOverrides
import cn.edu.qau.timetable.core.GlassEffect
import cn.edu.qau.timetable.core.SilenceMode
import cn.edu.qau.timetable.core.UiStyle
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
    /**
     * 调休安排：哪些日期按哪一天的课表上课、哪些日期放假。
     *
     * 存在 DataStore 而不是数据库表里 —— 一学期就这么几条，
     * 而给 Room 加表要升 schema 版本，本 App 用的是
     * `fallbackToDestructiveMigration()`，升版本会把用户的课表成绩清掉。
     */
    val dayOverrides: List<DayOverride> = emptyList(),
    /** Material You 动态取色（跟随手机壁纸）。只对 Material 3 风格生效。 */
    val dynamicColor: Boolean = true,
    /** 界面风格：Material 3（默认）或 MIUI X。 */
    val uiStyle: UiStyle = UiStyle.MATERIAL3,
    /**
     * 底栏的玻璃效果：无 / 高斯模糊。
     *
     * 对原版底栏和悬浮底栏都生效 —— 选「无」时底栏会回到完全不透明、
     * 由 Scaffold 正常占位的那副样子。
     */
    val glassEffect: GlassEffect = GlassEffect.GAUSSIAN,
    /**
     * 悬浮底栏。
     *
     * 打开：底栏是一枚浮在内容之上的玻璃胶囊，内容从底下穿过去、被模糊。
     * 关闭：回到原来那种贴底、占位、不透明的 NavigationBar。
     *
     * 留这个开关是因为悬浮底栏依赖 GraphicsLayer 录屏 + 模糊，
     * 属于"能用但没必要人人都用"的东西 —— 而且有人就是更习惯原版那种踏实感。
     */
    val floatingBottomBar: Boolean = true,
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
            dayOverrides = DayOverrides.decode(p[KEY_DAY_OVERRIDES].orEmpty()),
            dynamicColor = p[KEY_DYNAMIC_COLOR] ?: true,
            uiStyle = UiStyle.fromName(p[KEY_UI_STYLE]),
            glassEffect = GlassEffect.fromName(p[KEY_GLASS_EFFECT]),
            floatingBottomBar = p[KEY_FLOATING_BAR] ?: true,
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

    suspend fun setUiStyle(style: UiStyle) = edit {
        it[KEY_UI_STYLE] = style.name
    }

    suspend fun setGlassEffect(effect: GlassEffect) = edit {
        it[KEY_GLASS_EFFECT] = effect.name
    }

    suspend fun setFloatingBottomBar(enabled: Boolean) = edit {
        it[KEY_FLOATING_BAR] = enabled
    }

    /** 整份替换调休安排（增删都在 UI 侧算好再写回）。 */
    suspend fun setDayOverrides(list: List<DayOverride>) = edit {
        it[KEY_DAY_OVERRIDES] = DayOverrides.encode(list)
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
        val KEY_DAY_OVERRIDES = stringPreferencesKey("day_overrides")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_UI_STYLE = stringPreferencesKey("ui_style")
        val KEY_GLASS_EFFECT = stringPreferencesKey("glass_effect")
        val KEY_FLOATING_BAR = booleanPreferencesKey("floating_bottom_bar")
        val KEY_KB_URL = stringPreferencesKey("url_kb")
        val KEY_EXAM_URL = stringPreferencesKey("url_exam")
        val KEY_GRADE_URL = stringPreferencesKey("url_grade")
        val KEY_CLASSROOM_URL = stringPreferencesKey("url_classroom")
    }
}
