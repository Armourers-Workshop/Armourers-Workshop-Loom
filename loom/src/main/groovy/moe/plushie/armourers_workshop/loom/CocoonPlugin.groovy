package moe.plushie.armourers_workshop.loom

import moe.plushie.armourers_workshop.loom.core.task.ConfigTask
import moe.plushie.armourers_workshop.loom.core.task.LoomTestTask
import moe.plushie.armourers_workshop.loom.core.task.SignJarTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

class CocoonPlugin implements Plugin<Project> {

    public static Project commonProject

    @Override
    void apply(Project project) {
        // get the project config of the minecraft version/
        project.extensions.minecraft_version_number = ConfigTask.parseVersion(project.rootProject.architectury.minecraft, 0)
        project.extensions.create("cocoon", CocoonPluginExt.class, project)

        // create the custom tasks.
        project.tasks.register("runClientTest", LoomTestTask.class)
        project.tasks.register("runServerTest", LoomTestTask.class)
        project.tasks.register("signJar", SignJarTask.class)
        project.tasks.register("processMixinResources", ConfigTask.class)

        // setup the custom tasks depends.
        project.tasks["signJar"].dependsOn("remapSourcesJar")
        project.tasks["remapJar"].finalizedBy("signJar")

        // setup the evaluate tasks.
        project.beforeEvaluate {
            (project.tasks["processMixinResources"] as ConfigTask).setup()
        }

        // setup the extension methods.
        project.extensions.modOptionalApi = { lib ->
            if (lib != "") {
                project.dependencies.modApi(lib)
            }
        }

        project.extensions.modOptionalCompileOnly = { lib ->
            if (lib != "") {
                project.dependencies.modCompileOnly(lib)
            }
        }

        project.extensions.modInclude = { lib ->
            project.dependencies.modApi(lib)
            project.dependencies.include(lib)
        }

        project.extensions.modShadow = { lib ->
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

        if (project.minecraft_version_number == 11605) {
            fixes(project)
            downgrade(project)
        }
    }

    void fixes(Project project) {
        project.configurations.configureEach {
            it.resolutionStrategy {
                it.force "net.fabricmc:fabric-loader:0.15.10"

                // 3.2.1 does not support arm64 architecture
                if (DefaultNativePlatform.currentArchitecture.arm) {
                    return
                }

                it.force "org.lwjgl:lwjgl-stb:3.2.1"
                it.force "org.lwjgl:lwjgl-opengl:3.2.1"
                it.force "org.lwjgl:lwjgl-openal:3.2.1"
                it.force "org.lwjgl:lwjgl-tinyfd:3.2.1"
                it.force "org.lwjgl:lwjgl-jemalloc:3.2.1"
                it.force "org.lwjgl:lwjgl-glfw:3.2.1"
                it.force "org.lwjgl:lwjgl:3.2.1"
            }
        }

        project.dependencies {
            it.runtimeOnly "org.slf4j:slf4j-api:1.7.25"
        }
    }

    void downgrade(Project project) {
        // in develop mode, we don’t need to downgrade.
        if (System.getProperty("jabel.active") == "false" || System.getProperty('idea.active') == "true") {
            return
        }

        project.dependencies {
            // add downgrade plugin in the compile time.
            it.annotationProcessor "com.pkware.jabel:jabel-javac-plugin:1.0.1-1"
        }

        project.tasks.withType(JavaCompile) {
            // force output java version to java 8.
            it.options.release = 8
        }

        project.processResources {
            // fix compatibility level to java 8 in mixin json.
            it.inputs.property('compatibilityLevel', 8)
            it.filesMatching("*-mixins.json") {
                it.filter {
                    it.replaceAll(/("compatibilityLevel\"\s*:\s*")(JAVA_\d+)(")/, /$1JAVA_8$3/)
                }
            }
        }
    }
}
