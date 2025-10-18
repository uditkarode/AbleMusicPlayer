plugins {
    alias(libs.plugins.able.application)
}

android {
    namespace = "io.github.uditkarode.able"
    ndkVersion = "21.0.6113669"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.uditkarode.able"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "InterdimensionalBoop"

        externalNativeBuild {
            cmake {
                cppFlags("")
            }
        }
        buildFeatures {
            viewBinding = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = System.getenv("STORE_PASS")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASS")
        }
    }

    buildTypes {
        getByName("release") {
            postprocessing {
                isRemoveUnusedCode = true
                isObfuscate = false
                isOptimizeCode = true
                proguardFiles("proguard-rules.pro")
            }
            signingConfig = signingConfigs["release"]
        }
    }

    externalNativeBuild {
        cmake {
            path("CMakeLists.txt")
        }
    }
    packagingOptions {
        jniLibs {
            excludes += listOf("lib/x86_64/**", "lib/armeabi-v7a/**", "lib/x86/**")
        }
        resources {
            excludes += listOf("lib/x86_64/**", "lib/armeabi-v7a/**", "lib/x86/**")
        }
    }
}

dependencies {
    implementation(projects.core.model)

    implementation(libs.compose.runtime)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(libs.kotlin.stdlib.jdk7)
    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.material)
    implementation(libs.calligraphy3)
    implementation(libs.viewpump)
    implementation(libs.recyclerview)
    implementation(libs.core)
    implementation(libs.material.dialogs.input)
    implementation(libs.okhttp)
    implementation(libs.newpipeextractor)
    //noinspection GradleDependency
    implementation(libs.material.intro.screen)
    implementation(libs.lottie)
//    implementation(libs.gradient)
    implementation(libs.gson)
    implementation(libs.material.dialogs.bottomsheets)
    implementation(libs.glide)
    implementation(libs.analytics)
    implementation(libs.roundedimageview)
    implementation(libs.preference.ktx)
    implementation(libs.preferencex)
    implementation(libs.coordinatorlayout)
    implementation(libs.constraintlayout)
//    implementation(libs.xfetch2)
    implementation(libs.work.runtime)
    implementation(libs.palette.ktx)
    implementation(libs.jaudiotagger.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.jetbrains.kotlinx.coroutines.android)
    implementation(libs.lifecycle.viewmodel.ktx)
    annotationProcessor(libs.glide.compiler)
    implementation(files("../app/src/main/libs/mobile-ffmpeg.aar"))
}

//sourceSets {
//    getByName("main").java.srcDirs("src/main/kotlin")
//}