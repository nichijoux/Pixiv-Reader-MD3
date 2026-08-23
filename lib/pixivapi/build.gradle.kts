plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.pixiv.api"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// vendor 的 pixiv API 源码副本（lib 模块内适配：修复原仓库的注释/构建问题）
// 修改请只在 lib/pixivapi/ 内进行。
android.sourceSets["main"].manifest.srcFile("src/main/AndroidManifest.xml")

dependencies {
    api(libs.pixiv.login)              // OAuth PKCE / 密钥 / token 刷新
    api(libs.retrofit)
    api(libs.retrofit.converter.gson)
    api(libs.okhttp)
    api(libs.okhttp.logging)
    api(libs.gson)
    api(libs.kotlinx.coroutines.android)
}
