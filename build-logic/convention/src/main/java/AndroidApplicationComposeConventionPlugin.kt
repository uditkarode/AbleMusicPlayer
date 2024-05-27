
//class AndroidApplicationComposeConventionPlugin: Plugin<Project> {
//    override fun apply(target: Project) {
//        with(target) {
//            pluginManager.apply {
//                apply("com.android.application")
//                apply("org.jetbrains.kotlin.android")
//                apply("madifiers.spotless")
//            }
//
//            extensions.configure<BaseAppModuleExtension> {
//                configureAndroidCompose(this)
//                configureKotlinAndroid(this)
//            }
//        }
//    }
//}