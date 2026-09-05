plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.pixiv.reader.core.novel"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(libs.jsoup)
    testImplementation(libs.junit)
    // org.json 为 Android 内置类，本地 JVM 单测需引入同 API 的独立实现
    testImplementation("org.json:json:20240303")
}
