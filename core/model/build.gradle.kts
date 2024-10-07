plugins {
    id("kotlin-android")
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "io.github.uditkarode.able.model"
    compileSdk = 34
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.gson)
    implementation(libs.core.ktx)
}