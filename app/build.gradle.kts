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
        applicationId = "cn.edu.qau.timetable"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
        resourceConfigurations += listOf("zh", "en")
    }

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
            isMinifyEnabled = false
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
