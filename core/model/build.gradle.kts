plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "io.github.uditkarode.able.model"
    compileSdk = 36
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.gson)
    implementation(libs.core.ktx)
}
