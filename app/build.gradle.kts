import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// ---------------------------------------------------------------------------
// 签名口令**不进仓库**。
//
// 从项目根目录的 keystore.properties 读取，该文件已在 .gitignore 里。
// 密钥本身（*.jks）同样不入库 —— 一旦泄露，任何人都能用你的证书签出
// "同一个 App"，对已安装用户做覆盖升级。
//
// 文件不存在时：release 产出未签名包，debug 用 AGP 默认调试证书，
// 其余构建照常 —— 保证别人 clone 下来就能编译。
// ---------------------------------------------------------------------------
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasKeystore: Boolean = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "cn.edu.qau.timetable"
    compileSdk = 35

    defaultConfig {
        // 完整版的身份放在默认配置里；Lite 变体会在 flavor 里覆盖 applicationId。
        applicationId = "cn.edu.qau.timetable"
        minSdk = 26
        targetSdk = 35
        // 版本号由 flavor 各自决定，这里只是兜底
        versionCode = 17
        versionName = "3.4.1"
        resourceConfigurations += listOf("zh", "en")
    }

    // ------------------------------------------------------------------
    // 两个变体：完整版 full / 精简版 lite
    //
    // 用 flavor 而不是复制一份工程，是因为课表、成绩、提醒、静音这些代码
    // 两个版本完全一样。复制一份出去之后，任何 bug 都要修两遍、
    // 两边会慢慢长歪 —— 到某个时间点就没人敢动 Lite 了。
    //
    // Lite 的差异只有三处：
    //   1. 不含 ui/kaoyan、core/kaoyan、data/kaoyan（用 srcDirs 排除）
    //   2. applicationId 不同，所以能和完整版同时安装
    //   3. 名称/图标/版本号独立
    // ------------------------------------------------------------------
    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            applicationId = "cn.edu.qau.timetable"
            versionCode = 17
            versionName = "3.4.1"
            buildConfigField("boolean", "IS_LITE", "false")
        }

        create("lite") {
            dimension = "edition"
            // 独立包名：与完整版互不覆盖，可以同时装
            applicationId = "cn.edu.qau.timetable.lite"
            versionCode = 1
            versionName = "1.0.0"
            buildConfigField("boolean", "IS_LITE", "true")
            // 桌面上显示的名字与完整版区分开（写在各 flavor 的 strings.xml 里）
        }
    }

    // ------------------------------------------------------------------
    // Lite 变体怎么做到"不含考研"
    //
    // 一开始想直接 exclude "**/kaoyan/**"，但考研并不是一个独立目录 ——
    // 它横穿 7 个共用文件（MainActivity 的 Tab、QauApp 的仓库注入、
    // Daos 的表、Entities 的实体、SettingsStore 的字段、MainViewModel 的参数、
    // SettingsScreen 的输入框）。整目录排除会直接编译失败。
    //
    // 改用"单一接缝"：把「考研页 + 入口开关」收进一个 KaoyanHost.kt，
    // 这个文件在 full / lite 两个 flavor 里各有一份实现：
    //   · full：hasKaoyan = true，渲染真正的 KaoyanScreen
    //   · lite：hasKaoyan = false，渲染一句说明
    // 其余代码一律读 hasKaoyan，不再直接引用 KaoyanScreen。
    //
    // 这样 lite 里 KaoyanScreen 及其依赖在 R8 看来是**不可达**的，
    // 会被直接裁掉 —— 体积收益相当于排除目录，但不用把共用文件复制两份。
    // ------------------------------------------------------------------
    // ------------------------------------------------------------------
    // 注：本想给 lite 变体排除 assets/kaoyan/**（种子数据 0.37 MB），
    // 但 AGP 的 Kotlin DSL 在 android{} 里拿不到 assets 这个 SourceDirectorySet
    // （只有 java/assets 的 variant API 才行）。为 0.37 MB 引入一段
    // androidComponents 回调不值当 —— 更直接的做法是让 Lite 版
    // 不调用 ensureSeeded()：R8 判定 ensureSeeded 不可达后，
    // 它引用的 assets 名字虽然还在 APK 里，但这段代码已经不存在，
    // 也就不会去读它。真要彻底去掉，等以后有别的体积需求再一起处理。
    // ------------------------------------------------------------------


    signingConfigs {
        // 正式的本地签名密钥（自签，仅用于个人侧载）。
        // 用 debug 证书时，部分国内 ROM 的安装器会以"签名校验不通过"拒绝，
        // 换成正式的 RSA-4096 自签证书可以避开这类校验。
        if (hasKeystore) {
            create("localRelease") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
        getByName("debug") {
            // 以前这里用的是 AGP 自动生成的 "Android Debug" 证书，
            // 结果 debug 包和 release 包证书不同 —— 两个包互相覆盖安装时，
            // 系统会直接拒绝并报「签名校验不通过」。
            // 现在统一到下面 buildTypes 里指定的同一把密钥。
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // 关键：debug 也使用同一把正式密钥。
            // 两个变体证书一致，才能互相覆盖安装/升级；
            // 证书不一致时系统会直接报「签名校验不通过」。
            if (hasKeystore) signingConfig = signingConfigs.getByName("localRelease")
        }
        release {
            // 侧载用的正式包走 localRelease 签名（缺 keystore.properties 时留空 = 未签名）
            if (hasKeystore) signingConfig = signingConfigs.getByName("localRelease")

            // ------------------------------------------------------------------
            // 开启 R8（代码压缩 + 混淆 + 资源压缩）
            //
            // 之前一直是 false，代价很大：实测 release APK 里 dex 未压缩就有
            // 43.57 MB（3 个 dex），占了整个包体积的 96% —— 绝大部分是
            // Compose / Material3 / material-icons-extended 里根本没用到的代码。
            // 开启 R8 后这些会被裁掉，这是这个项目最大的一块体积优化。
            //
            // 混淆（obfuscate）一并打开：Compose 与 Room 都有官方 consumer rules，
            // 不需要靠反射保留类名；真正的风险点是 WebView 的 @JavascriptInterface
            // （按名字被 JS 调用，混淆后就断）和 Room 实体字段名，
            // 这两个已在 proguard-rules.pro 里显式保留。
            // ------------------------------------------------------------------
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // ---------------------------------------------------------------------
    // Compose 编译器报告（性能优化的测量口径）
    //
    // 跑 `.\gradlew.bat :app:compileDebugKotlin -PcomposeMetrics` 会在
    // app/build/compose_compiler/ 下产出：
    //   · *-composables.txt —— 每个 composable 是否 skippable / restartable
    //   · *-classes.txt     —— 每个类被判成 stable 还是 unstable
    // 前者回答「父级重组时这个函数能不能被跳过」，后者回答「为什么跳不过」。
    //
    // 只在显式传 -PcomposeMetrics 时打开：默认构建不写这些文件，
    // 免得日常编译和增量缓存多出一堆产物。
    // ---------------------------------------------------------------------
    composeCompiler {
        if (project.hasProperty("composeMetrics")) {
            metricsDestination = layout.buildDirectory.dir("compose_compiler")
            reportsDestination = layout.buildDirectory.dir("compose_compiler")
        }
    }

    lint {
        // AGP 8.7 的 lint 检测器自身会崩：
        //   IncompatibleClassChangeError in NonNullableMutableLiveDataDetector
        // 这是工具链 bug，不是本项目代码的问题（debug 包一直构建正常）。
        // 关掉 release 的 fatal lint，避免它挡住出包。
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    // LocalLifecycleOwner（设置页从系统权限页返回时重读授权状态）。
    // 它同时也是 compose-ui 里那个已弃用的同名 API 的新家，显式声明免得跟着传递依赖漂。
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // 动画：AnimatedContent / Transition / 弹簧规格。
    // material3 会把它作为传递依赖带进来，但显式声明才不会在依赖变化时突然消失。
    implementation("androidx.compose.animation:animation")

    // Material 3 Expressive 组件（MaterialShapes / ButtonGroup / FloatingToolbar 等）
    // 从 1.4.0 开始提供。
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
