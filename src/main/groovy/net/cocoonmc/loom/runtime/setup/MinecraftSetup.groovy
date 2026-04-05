package net.cocoonmc.loom.runtime.setup

import net.cocoonmc.loom.api.CocoonExtension
import net.cocoonmc.loom.runtime.CocoonExtensionImpl
import org.gradle.api.Action
import org.gradle.api.Project

class MinecraftSetup implements Action<Object> {

    private final Project project
    private final CocoonExtensionImpl cocoon

    MinecraftSetup(Project project) {
        this.project = project
        this.cocoon = project.extensions.getByType(CocoonExtension) as CocoonExtensionImpl
    }

    @Override
    void execute(Object object) {
        // configure the minecraft version.
        cocoon.architectury.minecraft = cocoon.minecraft.get()

        // call the details actions.
        detailsAction.execute(object)
    }

    private Action<Object> getDetailsAction() {
        if (cocoon.disableObfuscation) {
            return new WithoutObfuscationDetails()
        }
        return new ObfuscationDetails()
    }

    private class ObfuscationDetails implements Action<Object> {

        @Override
        void execute(Object object) {
            // configure the minecraft dependency.
            project.dependencies.add("minecraft", cocoon.resolveMinecraftDependency())
            project.dependencies.add("mappings", cocoon.resolveMappingsDependency())
        }
    }

    private class WithoutObfuscationDetails implements Action<Object> {

        @Override
        void execute(Object object) {
            // configure the minecraft dependency.
            project.dependencies.add("minecraft", cocoon.resolveMinecraftDependency())

            // redirect the missing configuration to default implements.
            redirect("modApi", "compileOnly")
            redirect("modCompileOnly", "compileOnly")
            redirect("modImplementation", "compileOnly")
            redirect("modLocalRuntime", "localRuntime")

            redirect("modApi", "localRuntime")
            redirect("modImplementation", "localRuntime")
        }

        private void redirect(String from, String to) {
            var configuration = project.configurations.maybeCreate(from)
            project.configurations.maybeCreate(to).extendsFrom(configuration)
        }
    }
}
