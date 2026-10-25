plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pixiv.reader.feature.user"
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
    implementation(project(":core:comment"))
    api(project(":core:database"))
    api(project(":core:datastore"))
    api(libs.androidx.navigation.compose)

    api(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    // SAF 目录访问（下载位置 DocumentFile）
    implementation(libs.androidx.documentfile)
    // WorkManager（删除未完成下载时取消在途后台任务）
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
}
