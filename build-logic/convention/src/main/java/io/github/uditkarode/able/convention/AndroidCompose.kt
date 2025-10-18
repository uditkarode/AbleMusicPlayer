package io.github.uditkarode.able.convention

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * shared compose configuration plugin
 */
internal fun Project.configureAndroidCompose(
    commonExtension: CommonExtension<*, *, *, *, *, *>
) {
    commonExtension.apply {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        buildFeatures {
            compose = true
        }

        packaging {
            resources {
                excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            }
        }

        dependencies {
            val bom = libs.findLibrary("compose-bom").get()
            add("implementation", platform(bom))
            add("debugImplementation", libs.findLibrary("compose-ui-manifest").get())
            add("androidTestImplementation", platform(bom))
        }
    }
}