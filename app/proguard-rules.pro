# 青农课表 ProGuard 规则
# Room / Compose 基本不需要额外规则，保留实体类以防万一
-keep class cn.edu.qau.timetable.data.model.** { *; }
-dontwarn org.jetbrains.annotations.**
