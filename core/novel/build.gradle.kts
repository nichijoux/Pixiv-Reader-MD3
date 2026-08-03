plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.pixiv.reader.core.novel"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(libs.jsoup)
    testImplementation(libs.junit)
    // org.json 为 Android 内置类，本地 JVM 单测需引入同 API 的独立实现
    testImplementation("org.json:json:20240303")
}
