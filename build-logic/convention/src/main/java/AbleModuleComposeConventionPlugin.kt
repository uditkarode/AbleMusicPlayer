import com.android.build.gradle.LibraryExtension
import io.github.uditkarode.able.convention.configureAndroidCompose
import io.github.uditkarode.able.convention.configureKotlinAndroid
import io.github.uditkarode.able.convention.kotlinOptions
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AbleModuleComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")

            extensions.configure<LibraryExtension> {
                configureAndroidCompose(this)
                configureKotlinAndroid(this)

                kotlinOptions {
                    freeCompilerArgs = freeCompilerArgs
                }
            }
        }
    }
}