package io.github.uditkarode.able.convention

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import java.io.File

/**
 * common configuration for android compose
 */
//internal fun Project.configureAndroidCompose(
//    commonExtension: CommonExtension<*, *, *, *, *, *>
//) {
//    commonExtension.apply {
//        buildFeatures {
//            compose = true
//        }
//
//        dependencies {
//            val bom = libs.findLibrary("compose-bom").get()
//            add("implementation", platform(bom))
//            add("androidTestImplementation", platform(bom))
//            add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
//            add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
//        }
//    }

//    extensions.configure<ComposeCompilerGradlePluginExtension> {
//        fun Provider<String>.onlyIfTrue() = flatMap { provider { it.takeIf(String::toBoolean) } }
//        fun Provider<*>.relativeToRootProject(dir: String) = flatMap {
//            rootProject.layout.buildDirectory.dir(projectDir.toRelativeString(rootDir))
//        }.map { it.dir(dir) }
//
//        project.providers.gradleProperty("enableComposeCompilerMetrics").onlyIfTrue()
//            .relativeToRootProject("compose-metrics")
//            .let(metricsDestination::set)
//
//        project.providers.gradleProperty("enableComposeCompilerReports").onlyIfTrue()
//            .relativeToRootProject("compose-reports")
//            .let(reportsDestination::set)
//    }
//}