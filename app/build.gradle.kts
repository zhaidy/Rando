plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // 界面截图测试：在电脑上把 Compose 界面渲染成图片（./gradlew recordRoborazziDebug）
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.personal.rando"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.personal.rando"
        minSdk = 26
        targetSdk = 36
        // CI 每次构建递增，和 Release 的「Rando build N」对得上
        val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
        // MapLibre 的原生库每种 CPU 一份；现在的手机都是 arm64，只留这一份，APK 小一大半
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // 固定签名：每次构建签名一致，新 APK 才能直接覆盖安装（数据保留）。
    // 密钥来自 CI 的 Secrets；本地没配置时退回 Android 默认调试签名。
    val keystorePath = System.getenv("RANDO_KEYSTORE_FILE")
    if (keystorePath != null) {
        signingConfigs {
            create("rando") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RANDO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RANDO_KEY_ALIAS")
                keyPassword = System.getenv("RANDO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("rando")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("rando") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // 截图测试要读 Android 资源
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.maplibre)
    // 活动识别：检测到在骑车就自动开始记录
    implementation(libs.play.services.location)
    // 对讲：离得近的队友用蓝牙、Wi-Fi 直连
    implementation(libs.play.services.nearby)
    // 液态玻璃：面板折射、模糊后面的地图
    implementation(libs.haze)
    implementation(libs.haze.glass)

    testImplementation(libs.junit)
    // Android 自带的 org.json 在单元测试里是空壳，换成真实现
    testImplementation(libs.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.withType<Test>().configureEach {
    // 截图测试要下一个 100 MB 的 Android 运行时，平时（包括 CI）不跑；要看界面时加 -Pscreenshots
    if (!project.hasProperty("screenshots")) exclude("**/screenshots/**")
    // 云端会话里 Maven Central 常被限流，可以用环境变量换成镜像
    System.getenv("ROBOLECTRIC_REPO")?.let { systemProperty("robolectric.dependency.repo.url", it) }
}
