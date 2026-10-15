package cn.edu.qau.timetable.core

/**
 * 界面风格。
 *
 * 两套风格只替换 `MaterialTheme` 的**配色 / 圆角刻度 / 字体**，界面结构一律不动 ——
 * 所有页面用的都是 M3 组件、并且都从 `MaterialTheme` 取这三样，所以换一套 Theme
 * 就能整体改观，不需要给每个页面写第二套布局。
 *
 * 放在 `core/` 而不是 `ui/`：设置存在数据层，如果枚举定义在 UI 层，
 * 数据层就要反向依赖 UI 层。这一点和 [Campus]、[SilenceMode] 同理。
 */
enum class UiStyle(val label: String, val description: String) {
    /** 默认：农大绿 + Material 3 Expressive。 */
    MATERIAL3(
        label = "Material 3",
        description = "农大绿配色，可跟随便签取色",
    ),

    /** MIUI X / HyperOS 风味。 */
    MIUIX(
        label = "MIUI X",
        description = "澎湃风格：灰底白卡、大圆角、MIUI 蓝",
    ),
    ;

    companion object {
        fun fromName(name: String?): UiStyle =
            entries.firstOrNull { it.name == name } ?: MATERIAL3
    }
}
