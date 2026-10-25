plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pixiv.reader.feature.reader"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
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
    api(project(":core:novel"))
    api(libs.androidx.navigation.compose)

    api(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    // 简繁转换（OpenCC JNI，词典内置于 aar assets）
    implementation(libs.android.opencc)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
