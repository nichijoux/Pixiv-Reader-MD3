plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.pixiv.reader.core.common"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(libs.androidx.core.ktx)
    // NovelCardData / toCardData（历史/下载页反序列化）需要 vendor 模型
    api(project(":lib:pixivapi"))
    testImplementation(libs.junit)
}
