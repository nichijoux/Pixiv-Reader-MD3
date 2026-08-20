plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pixiv.reader.feature.reader"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    api(project(":core:ui"))
    api(project(":core:model"))
    api(project(":core:network"))
    api(project(":core:database"))
    api(project(":core:datastore"))
    api(project(":core:novel"))
    api(libs.androidx.navigation.compose)

    api(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    // 简繁转换（OpenCC JNI，词典内置于 aar assets）
    implementation(libs.android.opencc)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
