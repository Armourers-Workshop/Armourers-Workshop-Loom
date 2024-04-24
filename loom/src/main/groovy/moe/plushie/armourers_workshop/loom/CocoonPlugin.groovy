package moe.plushie.armourers_workshop.loom


import org.gradle.api.Plugin
import org.gradle.api.Project

class CocoonPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {

        // add
        project.extensions.create("cocoon", CocoonPluginExt.class, project)


        // create the custom tasks.
        project.tasks.register("signJar", SignJarTask.class)
        project.tasks.register("processMixinResources", MixinConfigTask.class)

        // setup the custom tasks depends.
        project.tasks["signJar"].dependsOn("remapSourcesJar")
        project.tasks["remapJar"].finalizedBy("signJar")

        // setup the evaluate tasks.
        project.beforeEvaluate {
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

        project.dependencies.extensions.modShadow = { lib ->
            project.dependencies.implementation(lib)
            project.dependencies.shadowCommon(lib) {
                it.transitive = false
            }
        }
    }
}