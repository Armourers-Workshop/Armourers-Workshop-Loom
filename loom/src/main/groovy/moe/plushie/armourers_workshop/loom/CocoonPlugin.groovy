package moe.plushie.armourers_workshop.loom


import org.gradle.api.Plugin
import org.gradle.api.Project

class CocoonPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        // get the project config of the minecraft version/
        project.extensions.minecraft_version_number = ConfigTask.parseVersion(project.rootProject.architectury.minecraft, 0)
        project.extensions.create("cocoon", CocoonPluginExt.class, project)

        // create the custom tasks.
        project.tasks.register("signJar", SignJarTask.class)
        project.tasks.register("processMixinResources", ConfigTask.class)

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
            if (project.configurations.names.contains("forgeRuntimeLibrary")) {
                project.dependencies.forgeRuntimeLibrary(lib) {
                    it.transitive = false
                }
            }
        }

        if (project.minecraft_version_number === 11605) {
            fixes(project)
        }
    }

    void fixes(Project project) {
        project.configurations.configureEach {
            it.resolutionStrategy {
                it.force "org.lwjgl:lwjgl-stb:3.2.1"
                it.force "org.lwjgl:lwjgl-opengl:3.2.1"
                it.force "org.lwjgl:lwjgl-openal:3.2.1"
                it.force "org.lwjgl:lwjgl-tinyfd:3.2.1"
                it.force "org.lwjgl:lwjgl-jemalloc:3.2.1"
                it.force "org.lwjgl:lwjgl-glfw:3.2.1"
                it.force "org.lwjgl:lwjgl:3.2.1"

                it.force "net.fabricmc:fabric-loader:0.15.10"
            }
        }
    }
}