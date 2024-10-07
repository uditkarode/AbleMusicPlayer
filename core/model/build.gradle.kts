plugins {
    id("kotlin-android")
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "io.github.uditkarode.able.model"
    compileSdk = 34
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.gson)
    implementation(libs.core.ktx)
}