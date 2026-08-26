plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pixiv.reader.core.network"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}

dependencies {
    api(project(":lib:pixivapi"))
    api(project(":core:common"))
    api(project(":core:novel"))
    // 下载 worker 的 EntryPoint 需要下载索引 DAO
    api(project(":core:database"))

    api(libs.mmkv)
    api(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    // RankingPagedViewModel 基类（viewModelScope）
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    // 插画下载 Worker（IllustDownloadWorker 下沉至此）
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
