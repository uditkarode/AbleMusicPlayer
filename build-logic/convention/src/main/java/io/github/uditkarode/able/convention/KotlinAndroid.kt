package io.github.uditkarode.able.convention

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension<*, *, *, *, *, *>
) {
    commonExtension.apply {
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        tasks.withType<KotlinCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(libs.findVersion("jvmTarget").get().toString()))
                freeCompilerArgs.addAll(
                    listOf(
                        "-opt-in=kotlin.RequiresOptIn",
                        "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    )
                )
            }
        }

        lint {
            abortOnError = false
        }
    }
}