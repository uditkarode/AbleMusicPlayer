plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "io.github.uditkarode.able"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.uditkarode.able"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "ConcentricPuddles"

        buildFeatures {
            viewBinding = true
            buildConfig = true
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
}

dependencies {
    implementation(projects.core.model)

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.material)
    implementation(libs.calligraphy3)
    implementation(libs.viewpump)
    implementation(libs.recyclerview)
    implementation(libs.viewpager2)
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