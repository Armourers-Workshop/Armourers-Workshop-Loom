package net.cocoonmc.loom.runtime

import dev.architectury.plugin.ArchitectPluginExtension
import net.cocoonmc.loom.api.CocoonExtension
import net.cocoonmc.loom.api.CocoonSettings
import net.cocoonmc.loom.core.Platform
import net.cocoonmc.loom.core.Version
import net.cocoonmc.loom.runtime.setup.CommonSetup
import net.cocoonmc.loom.runtime.setup.PlatformSetup
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition

class CocoonExtensionImpl implements CocoonExtension {

    private final Project project
    private final Platform platform

    private final Property<String> minecraft
    private final Property<String> mappings
    private final Property<Boolean> compileOnly

    CocoonExtensionImpl(Project project) {
        this.project = project
        this.platform = Platform.by(project)
        this.minecraft = project.objects.property(String)
        this.mappings = project.objects.property(String).convention("official")
        this.compileOnly = project.objects.property(Boolean).convention(false)
        // link the property to a root project.
        if (project != project.rootProject) {
            def parent = project.rootProject.extensions.getByType(CocoonExtension)
            minecraft.convention(parent.minecraft)
            mappings.convention(parent.mappings)
            compileOnly.convention(parent.compileOnly)
        }
    }

    @Override
    void common(Action<? super CocoonSettings> action) {
        var setup = new CommonSetup(project)
        def settings = new CocoonSettingsImpl(project, platform)
        action.execute(settings)
        setup.execute(settings)
    }

    @Override
    void fabric(Action<? super CocoonSettings> action) {
        var setup = new PlatformSetup(project, Platform.FABRIC)
        def settings = new CocoonSettingsImpl(project, platform)
        action.execute(settings)
        setup.execute(settings)
    }

    @Override
    void forge(Action<? super CocoonSettings> action) {
        var setup = new PlatformSetup(project, platform)
        def settings = new CocoonSettingsImpl(project, platform)
        action.execute(settings)
        setup.execute(settings)
    }

    @Override
    Property<String> getMinecraft() {
        return minecraft
    }

    @Override
    Property<String> getMappings() {
        return mappings
    }

    @Override
    Property<Boolean> getCompileOnly() {
        return compileOnly
    }

    int getMinecraftNumber() {
        return Version.parse(minecraft.get(), 0)
    }

    boolean getDisableObfuscation() {
        return getMinecraftNumber() >= 260000
    }

    Platform getPlatform() {
        return platform
    }

    LoomGradleExtensionAPI getLoom() {
        return project.extensions.getByType(LoomGradleExtensionAPI.class)
    }

    ArchitectPluginExtension getArchitectury() {
        return project.extensions.getByType(ArchitectPluginExtension.class)
    }

    Object resolveApiDependency(CocoonSettings settings) {
        switch (platform) {
            case Platform.FORGE:
                return "net.minecraftforge:forge:${settings.api.get()}"
            case Platform.NEOFORGE:
                return "net.neoforged:neoforge:${settings.api.get()}"
            case Platform.FABRIC:
            case Platform.UNKNOWN:
                return "net.fabricmc.fabric-api:fabric-api:${settings.api.get()}"
        }
    }

    Object resolveLoaderDependency(CocoonSettings settings) {
        switch (platform) {
            case Platform.FORGE:
                return "net.minecraftforge:forge:${settings.api.get()}"
            case Platform.NEOFORGE:
                return "net.neoforged:neoforge:${settings.api.get()}"
            case Platform.FABRIC:
            case Platform.UNKNOWN:
                return "net.fabricmc:fabric-loader:${settings.loader.get()}"
        }
    }

    Object resolveMinecraftDependency() {
        return "com.mojang:minecraft:" + minecraft.get()
    }

    Object resolveMappingsDependency() {
        return loom.officialMojangMappings()
    }
}
