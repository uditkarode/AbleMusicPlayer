plugins {
    alias(libs.plugins.able.library)
}

android {
    namespace = "io.github.uditkarode.able.model"
    compileSdk = 34
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.gson)
}