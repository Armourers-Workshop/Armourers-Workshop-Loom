package moe.plushie.armourers_workshop.loom

import org.gradle.api.Plugin
import org.gradle.api.Project

class Main implements Plugin<Project> {

    @Override
    void apply(Project project) {
        // create the custom tasks.
        project.tasks.register("signJar", SignJarTask.class)
        project.tasks.register("processPackResources", PackConfigTask.class)
        project.tasks.register("processMixinResources", MixinConfigTask.class)

        // setup the custom tasks depends.
        project.tasks["signJar"].dependsOn("remapSourcesJar")
        project.tasks["remapJar"].finalizedBy("signJar")

        // setup the evaluate tasks.
        project.beforeEvaluate {
            project.tasks["processPackResources"].attach()
            project.tasks["processMixinResources"].attach()
        }

        // setup the extension methods.
        project.dependencies.extensions.modOptionalApi = { lib ->
            if (lib != "") {
                project.dependencies.modApi(lib)
            }
        }
        project.dependencies.extensions.modOptionalCompileOnly = { lib ->
            if (lib != "") {
                project.dependencies.modCompileOnly(lib)
            }
        }
        project.dependencies.extensions.modInclude = { lib ->
            project.dependencies.modApi(lib)
            project.dependencies.include(lib)
        }
    }
}