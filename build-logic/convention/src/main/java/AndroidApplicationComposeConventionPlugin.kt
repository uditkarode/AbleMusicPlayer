import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import io.github.uditkarode.able.convention.configureAndroidCompose
import io.github.uditkarode.able.convention.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
            }

            extensions.configure<BaseAppModuleExtension> {
                configureAndroidCompose(this)
                configureKotlinAndroid(this)
            }
        }
    }
}