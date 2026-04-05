package net.cocoonmc.loom.runtime

import net.cocoonmc.loom.api.CocoonSettings
import net.cocoonmc.loom.core.Platform
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

class CocoonSettingsImpl implements CocoonSettings {

    private final Property<String> api
    private final Property<String> loader
    private final RegularFileProperty accessWidenerPath

    private final Platform platform

    CocoonSettingsImpl(Project project, Platform platform) {
        this.platform = platform
        this.api = project.objects.property(String)
        this.loader = project.objects.property(String)
        this.accessWidenerPath = project.objects.fileProperty()
    }

    @Override
    Property<String> getApi() {
        return api
    }

    @Override
    Property<String> getLoader() {
        return loader
    }

    @Override
    RegularFileProperty getAccessWidenerPath() {
        return accessWidenerPath
    }
}
