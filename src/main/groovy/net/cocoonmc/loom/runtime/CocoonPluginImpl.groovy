package net.cocoonmc.loom.runtime

import net.cocoonmc.loom.api.CocoonExtension
import net.cocoonmc.loom.core.task.ConfigTask
import net.cocoonmc.loom.core.task.JabelTask
import net.cocoonmc.loom.core.task.SignJarTask
import net.cocoonmc.loom.core.task.TestTask
import net.cocoonmc.loom.runtime.setup.MinecraftSetup
import org.gradle.api.Action
import org.gradle.api.Project

class CocoonPluginImpl {

    private final Project project
    private final CocoonExtensionImpl cocoon

    CocoonPluginImpl(Project project) {
        this.project = project
        this.cocoon = (CocoonExtensionImpl) project.extensions.create(CocoonExtension, "cocoon", CocoonExtensionImpl, project)
    }

    void prepare() {
        // enable the architectury plugin in the all projects.
        project.pluginManager.apply("java-library")
        project.pluginManager.apply("architectury-plugin")
    }

    void apply() {
        // enable the architectury loom in the subprojects.
        project.pluginManager.apply("dev.architectury.loom" + loomVersion)

        // add dependencies and configure tasks into the subprojects.
        setupOptionalDependencies()
        setupMinecraftDependencies()
        setupTestTasks()
        setupCompileTasks()
    }

    private void setupTestTasks() {
        project.tasks.register("runClientTest", TestTask)
        project.tasks.register("runServerTest", TestTask)
    }

    private void setupCompileTasks() {
        // create the compile tasks.
        project.tasks.register("jabel", JabelTask)
        project.tasks.register("signJar", SignJarTask)
        project.tasks.register("processMixinResources", ConfigTask)

        // setup the evaluate tasks.
        project.beforeEvaluate {
            runConfigTask("jabel")
            runConfigTask("processMixinResources")
        }
    }

    private void setupMinecraftDependencies() {
        // configure the minecraft info into project.
        var setup = new MinecraftSetup(project)
        setup.execute(project)
    }

    private void setupOptionalDependencies() {
        // setup the extension methods.
        project.extensions.add("modOptionalApi", {
            if (it != "") {
                project.dependencies.add("modApi", it)
            }
        })

        project.extensions.add("modOptionalCompileOnly", {
            if (it != "") {
                project.dependencies.add("modCompileOnly", it)
            }
        })

        project.extensions.add("modInclude", {
            project.dependencies.add("modApi", it)
            project.dependencies.add("include", it)
        })

        project.extensions.add("modShadow", {
            project.dependencies.add("implementation", it)
            if (project.configurations.names.contains("shadowCommon")) {
                project.dependencies.add("shadowCommon", it) {
                    it.transitive = false
                }
            }
            if (project.configurations.names.contains("forgeRuntimeLibrary")) {
                project.dependencies.add("forgeRuntimeLibrary", it) {
                    it.transitive = false
                }
            }
        })
    }

    private void runConfigTask(String name) {
        var task = project.tasks.named(name).get()
        if (task instanceof Action) {
            task.execute(cocoon)
        }
    }

    private String getLoomVersion() {
        if (cocoon.disableObfuscation) {
            return "-no-remap"
        }
        return ""
    }
}
