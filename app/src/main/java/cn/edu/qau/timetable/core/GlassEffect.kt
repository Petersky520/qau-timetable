package cn.edu.qau.timetable.core

/**
 * 底栏效果。
 *
 * - [GAUSSIAN]：把背后内容糊成一块磨砂玻璃，底栏半透明。
 * - [NONE]：底栏完全不透明，由 Scaffold 正常占位，内容不会钻到它底下。
 */
enum class GlassEffect(val label: String) {
    NONE("无"),
    GAUSSIAN("高斯模糊"),
    ;

    /** 要不要把背后内容模糊着画出来。 */
    val blursBackdrop: Boolean get() = this != NONE

    companion object {
        fun fromName(name: String?): GlassEffect =
            entries.firstOrNull { it.name == name } ?: GAUSSIAN
    }
}
