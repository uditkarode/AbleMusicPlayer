//apply plugin: 'com.android.application'
//apply plugin: 'kotlin-android'
//
//android {
//    lint {
//        disable 'ExpiredTargetSdkVersion'
//    }
//    namespace 'io.github.uditkarode.able'
//    ndkVersion '21.0.6113669'
//    compileSdkVersion 33
//    buildToolsVersion '30.0.3'
//    defaultConfig {
//        applicationId "io.github.uditkarode.able"
//        minSdkVersion 21
//        targetSdkVersion 29
//        versionCode 1
//        versionName "InterdimensionalBoop"
//
//        externalNativeBuild {
//            cmake {
//                cppFlags ""
//            }
//        }
//        buildFeatures {
//            viewBinding = true
//        }
//    }
//
////    compileOptions {
////        sourceCompatibility JavaVersion.VERSION_1_8
////        targetCompatibility JavaVersion.VERSION_1_8
////    }
//
//    kotlinOptions {
//        jvmTarget = JavaVersion.VERSION_1_8.toString()
//    }
//
//    signingConfigs {
//        release {
//            storeFile file("release.keystore")
//            storePassword System.getenv('STORE_PASS')
//            keyAlias System.getenv('KEY_ALIAS')
//            keyPassword System.getenv('KEY_PASS')
//        }
//    }
//
//    buildTypes {
//        release {
//            postprocessing {
//                removeUnusedCode true
//                obfuscate false
//                optimizeCode true
//                proguardFile 'proguard-rules.pro'
//            }
//            signingConfig signingConfigs.release
//        }
//    }
//
//    externalNativeBuild {
//        cmake {
//            path "CMakeLists.txt"
//        }
//    }
//    packagingOptions {
//        jniLibs {
//            excludes += ['lib/x86_64/**', 'lib/armeabi-v7a/**', 'lib/x86/**']
//        }
//        resources {
//            excludes += ['lib/x86_64/**', 'lib/armeabi-v7a/**', 'lib/x86/**']
//        }
//    }
//
//}
//
//dependencies {
//    implementation fileTree(dir: 'libs', include: ['*.jar'])
//    implementation libs.kotlin.stdlib.jdk7
//    implementation libs.appcompat
//    implementation libs.core.ktx
//    implementation libs.material
//    implementation libs.calligraphy3
//    implementation libs.viewpump
//    implementation libs.recyclerview
//    implementation libs.core
//    implementation libs.material.dialogs.input
//    implementation libs.okhttp
//    implementation libs.newpipeextractor
//    //noinspection GradleDependency
//    implementation libs.material.intro.screen
//    implementation libs.lottie
//    implementation libs.gradient
//    implementation libs.gson
//    implementation libs.material.dialogs.bottomsheets
//    implementation libs.glide
//    implementation libs.analytics
//    implementation libs.roundedimageview
//    implementation libs.preference.ktx
//    implementation libs.preferencex
//    implementation libs.coordinatorlayout
//    implementation libs.constraintlayout
//    implementation libs.xfetch2
//    implementation libs.work.runtime
//    implementation libs.palette.ktx
//    implementation libs.jaudiotagger.android
//    implementation libs.kotlinx.coroutines.core
//    implementation libs.jetbrains.kotlinx.coroutines.android
//    implementation libs.lifecycle.viewmodel.ktx
//    annotationProcessor libs.glide.compiler
//    implementation(name:'mobile-ffmpeg', ext:'aar')
//}
//
//android.sourceSets {
//    main.java.srcDirs += "src/main/kotlin"
//}

plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
//    lint {
//        disable("ExpiredTargetSdkVersion")
//    }

        namespace = "io.github.uditkarode.able"
    ndkVersion = "21.0.6113669"
    compileSdk = 34
//    buildToolsVersion '30.0.3'
    defaultConfig {
        applicationId = "io.github.uditkarode.able"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName= "InterdimensionalBoop"
//
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

//    buildTypes {
//        getByName("release") {
//            postprocessing {
//                removeUnusedCode = true
//                obfuscate = false
//                optimizeCode = true
//                proguardFiles("proguard-rules.pro")
//            }
//            signingConfig = signingConfigs["release"]
//        }
//    }

//    externalNativeBuild {
//        cmake {
//            path = "CMakeLists.txt"
//        }
//    }
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
    implementation(libs.gradient)
    implementation(libs.gson)
    implementation(libs.material.dialogs.bottomsheets)
    implementation(libs.glide)
    implementation(libs.analytics)
    implementation(libs.roundedimageview)
    implementation(libs.preference.ktx)
    implementation(libs.preferencex)
    implementation(libs.coordinatorlayout)
    implementation(libs.constraintlayout)
    implementation(libs.xfetch2)
    implementation(libs.work.runtime)
    implementation(libs.palette.ktx)
    implementation(libs.jaudiotagger.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.jetbrains.kotlinx.coroutines.android)
    implementation(libs.lifecycle.viewmodel.ktx)
    annotationProcessor(libs.glide.compiler)
    //C:\Users\Jayesh\AndroidStudioProjects\AbleMusicPlayer\app\src\main\libs\mobile-ffmpeg.aar
    implementation(files("../app/src/main/libs/mobile-ffmpeg.aar"))
//    implementation("com.github.HaarigerHarald:android-xliff-reader:1.0.0") { transitive = true }
}

//sourceSets {
//    getByName("main").java.srcDirs("src/main/kotlin")
//}