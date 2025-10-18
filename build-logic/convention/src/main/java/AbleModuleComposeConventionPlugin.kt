import com.android.build.gradle.LibraryExtension
import io.github.uditkarode.able.convention.configureAndroidCompose
import io.github.uditkarode.able.convention.configureKotlinAndroid
import io.github.uditkarode.able.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure

class AbleModuleComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")


            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                configureAndroidCompose(this)
            }

            tasks.withType(JavaCompile::class.java).configureEach {
                targetCompatibility = libs.findVersion("jvmTarget").get().toString()
                sourceCompatibility = libs.findVersion("jvmTarget").get().toString()
            }
        }
    }
}