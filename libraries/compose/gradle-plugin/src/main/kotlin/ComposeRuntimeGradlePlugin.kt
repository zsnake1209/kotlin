import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper

class ComposeRuntimeGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            (this as KotlinMultiplatformPluginWrapper)

            (project.extensions["kotlin"] as KotlinProjectExtension).apply {
                sourceSets.all {
                    if (name == "commonMain") {
                        kotlin.srcDir("...") // add src dir for common
                    }

                    if (name == "jsMain") {
                        kotlin.srcDir("...") // add src dir for js specific
                    }
                }
            }
        }
    }
}