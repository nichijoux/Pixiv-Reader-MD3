plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.pixiv.reader.core.common"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(libs.androidx.core.ktx)
    // NovelCardData / toCardData（历史/下载页反序列化）需要 vendor 模型
    api(project(":lib:pixivapi"))
    testImplementation(libs.junit)
}
