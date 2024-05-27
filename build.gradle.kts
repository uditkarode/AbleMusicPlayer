//buildscript {
////    ext.kotlin_version = "1.7.10"
//    repositories {
//        google()
//        mavenCentral()
//    }
//    dependencies {
//        classpath (libs.android.gradlePlugin)
//        classpath (libs.kotlin.gradlePlugin)
//    }
//}

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}


//allprojects {
//    repositories {
//        google()
//        jcenter()
//        mavenCentral()
//        maven { url 'https://jitpack.io' }
//        flatDir {
//            dirs 'src/main/libs'
//        }
//    }
//}

//task clean(type: Delete) {
//    delete rootProject.buildDir
//}
