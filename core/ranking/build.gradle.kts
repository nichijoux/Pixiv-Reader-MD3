plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pixiv.reader.core.ranking"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    // 排行榜竖切：列表组件依赖 UI 基建（三态/骨架/主题），VM 基类依赖分页与消息管线
    api(project(":core:ui"))
    api(project(":core:network"))
    implementation(libs.kotlinx.coroutines.android)
}
