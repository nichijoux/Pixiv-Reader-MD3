plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pixiv.reader.core.ui"
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
    api(project(":core:common"))
    api(project(":core:network"))

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.activity.compose)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.coil.compose)
    // 相册式图片缩放手势（捏合/双击/平移，与 Pager 自动协作）
    api(libs.telephoto.zoomable)
    // 简介富文本（Pixiv caption HTML → AnnotatedString）解析
    implementation(libs.jsoup)
    testImplementation(libs.junit)
}
