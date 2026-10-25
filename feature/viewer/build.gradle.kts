plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pixiv.reader.feature.viewer"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

// Compose 编译器稳定性配置：读取根目录 compose_stability.conf，把 Gson 解析模型与共享 UI 模型声明为 stable
composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability.conf"))
}

dependencies {
    api(project(":core:ui"))
    api(project(":core:network"))
    api(project(":core:database"))
    api(project(":core:datastore"))
    api(libs.androidx.navigation.compose)

    api(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    // 动图导出走 UgoiraExportWorker（WorkManager 后台执行）
    implementation(libs.androidx.work.runtime.ktx)
}
