plugins {
    alias(libs.plugins.able.library)
}

android {
    namespace = "io.github.uditkarode.able.services"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.glide)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.newpipeextractor)
    implementation(libs.work.runtime)
}