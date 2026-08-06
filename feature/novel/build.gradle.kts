plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pixiv.reader.feature.novel"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    api(project(":core:ui"))
    api(project(":core:model"))
    api(project(":core:network"))
    api(project(":core:database"))
    api(project(":core:novel"))
    api(libs.androidx.navigation.compose)

    implementation(project(":core:datastore"))
    api(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    // 后台下载（普通 Worker + EntryPoint，无需 @HiltWorker）
    implementation(libs.androidx.work.runtime.ktx)
    // PDF 导出
    implementation(libs.pdfbox)
    // SAF 目录访问（导出目录 DocumentFile）
    implementation(libs.androidx.documentfile)

    testImplementation(libs.junit)
}
