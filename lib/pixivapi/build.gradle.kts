plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.pixivapi"
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

// vendor 的 pixiv-api-kotlin 源码副本（lib 模块内适配：修复原仓库的注释/构建问题）
// 原仓库目录 pixiv-api-kotlin/ 只读不动；修改请同步到此处副本。
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
