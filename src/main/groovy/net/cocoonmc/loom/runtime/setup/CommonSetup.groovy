package net.cocoonmc.loom.runtime.setup

import net.cocoonmc.loom.CocoonPlugin
import net.cocoonmc.loom.api.CocoonExtension
import net.cocoonmc.loom.api.CocoonSettings
import net.cocoonmc.loom.runtime.CocoonExtensionImpl
import org.gradle.api.Action
import org.gradle.api.Project

class CommonSetup implements Action<CocoonSettings> {

    private final Project project
    private final CocoonExtensionImpl cocoon

    CommonSetup(Project project) {
        this.project = project
        this.cocoon = project.extensions.getByType(CocoonExtension) as CocoonExtensionImpl
        // the user mark this project is a common project.
        CocoonPlugin.commonProject = project
    }

    @Override
    void execute(CocoonSettings settings) {
        // configure the architectury minecraft version.
        cocoon.architectury.minecraft = cocoon.minecraft.get()

        // configure the access widener by the settings.
        cocoon.loom.accessWidenerPath.set(settings.accessWidenerPath)

        // We depend on fabric loader here to use the fabric @Environment annotations and get the mixin dependencies
        // Do NOT use other classes from fabric loader
        project.dependencies.add("modImplementation", cocoon.resolveLoaderDependency(settings))

        // disable common test task.
        project.tasks.named("runClientTest", it -> it.enabled = false)
        project.tasks.named("runServerTest", it -> it.enabled = false)

        // configure the platform by the settings.
        def projects = project.rootProject.subprojects.findAll { it != project }
        def platforms = projects.collect(it -> it.properties.get("loom.platform") as String)
        cocoon.architectury.common(platforms) {
            // map neoforge to forge
            it.platformPackage "neoforge", "forge"
        }

        // switch to the compile only mode if needs.
        if (cocoon.compileOnly.get()) {
            cocoon.architectury.compileOnly()
        }
    }
}
