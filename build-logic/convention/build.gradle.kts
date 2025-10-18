plugins {
    `kotlin-dsl`
}

group = "io.github.uditkarode.able.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidLibraryCompose") {
            id = "able.library"
            implementationClass = "AbleModuleComposeConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "able.android.application"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
    }
}