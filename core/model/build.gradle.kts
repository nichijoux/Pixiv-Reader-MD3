plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.pixiv.reader.core.model"
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
    // 复用 pixiv-api-kotlin 的 DTO（领域模型直接映射）
    api(project(":lib:pixivapi"))
    testImplementation(libs.junit)
}
