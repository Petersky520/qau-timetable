# ============================================================================
# 青小农 ProGuard / R8 规则
#
# 开启 R8 之后，混淆会**重命名**类、方法、字段。下面保留的都是「靠名字被外部
# 调用」的东西 —— 一旦被改名，功能会静默失效（不报错、不崩溃，只是不好用了），
# 是这类问题里最难查的一种。
# ============================================================================

# ---------------------------------------------------------------------------
# 1. WebView 的 JavaScript 桥（最高风险）
#
# @JavascriptInterface 标注的方法是被网页里的 JS 按**字符串名字**调用的，
# 编译期没有任何引用关系可追踪。混淆后 QauBridge.post() 会变成 a.b()，
# 而 JS 里还在调 QauBridge.post(...) —— 整个抓取功能直接哑掉，
# 而且 logcat 里只会看到一句 "not found"，极难联想到是混淆造成的。
#
# ⚠️ 这里踩过一次坑：最初写 `-keep class ...SyncScreen$QauBridge`，
# 结果它仍是 private 嵌套类，R8 照样重命名 —— 实测 full 变体的 dex 里
# 已经找不到 "QauBridge" 这个字符串，等于抓取全废。
#
# 但**不能**用 `-keep class * { @JavascriptInterface <methods>; }` 兜底：
# 那会把每个带该注解的类整个保留（含所有字段/方法），实测让 APK
# 从 1.74 MB 反弹到 2.93 MB —— 因为 Compose 里也有类带这个注解。
#
# 正确做法是两层：
#   ① 保留**方法**不混淆（JS 按方法名调 post）
#   ② 只对**我们自己的**桥类保留类名（用 keepclasseswithmembers 精确匹配，
#      它只作用于真正声明了该注解方法的类）
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclasseswithmembers,allowshrinking class cn.edu.qau.timetable.ui.** {
    @android.webkit.JavascriptInterface <methods>;
}

# ---------------------------------------------------------------------------
# 2. Room
#
# Room 在编译期生成 Xxx_Impl，运行时按名字反射找它；实体类的字段名
# 会直接当 SQL 列名用（@ColumnInfo 没写 name 时字段名即列名）。
# 混淆字段名会导致「建表用 a、查询用 b」这类完全对不上的错误。
# ---------------------------------------------------------------------------
-keep class cn.edu.qau.timetable.data.model.** { *; }
-keep class cn.edu.qau.timetable.data.db.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------
# 3. 数据类（Room 实体的伴生、DataStore 的枚举还原）
#
# AppSettings、各类枚举都靠 name/label 字符串做持久化还原
# （如 Campus.fromName、UiStyle.fromName）。混淆会把枚举常量的名字改掉，
# 已经存进 DataStore 的值就再也认不回来了 —— 用户设置全部回落默认。
# ---------------------------------------------------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class cn.edu.qau.timetable.core.** {
    <fields>;
}
-keep class cn.edu.qau.timetable.data.prefs.** { *; }

# ---------------------------------------------------------------------------
# 4. 通知 / 广播 / 小组件
#
# 这些组件在 AndroidManifest 里按**类名**注册，由系统实例化。
# R8 不会自动看 manifest 里的字符串，必须显式保留。
# ---------------------------------------------------------------------------
-keep class cn.edu.qau.timetable.notify.** { *; }
-keep class cn.edu.qau.timetable.widget.** { *; }
-keep class cn.edu.qau.timetable.MainActivity { *; }
-keep class cn.edu.qau.timetable.QauApp { *; }

# 小组件的 RemoteViews 通过资源 id 与布局名工作，资源压缩时不能动它们
-keep class * extends android.appwidget.AppWidgetProvider

# ---------------------------------------------------------------------------
# 5. Kotlin / 协程
# ---------------------------------------------------------------------------
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**

# ---------------------------------------------------------------------------
# 6. 其它
# ---------------------------------------------------------------------------
-dontwarn org.jetbrains.annotations.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# 保留行号与源文件名，线上崩溃栈才有意义（不保留局部变量名，省体积）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
