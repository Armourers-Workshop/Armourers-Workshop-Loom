package moe.plushie.armourers_workshop.loom

import net.fabricmc.loom.api.ModSettings
import net.fabricmc.loom.extension.LoomGradleExtensionApiImpl
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project

class CocoonPluginExt {

    public final Project project
    public final Platform platform

    public boolean compileOnlyMode = true

    CocoonPluginExt(Project project) {
        this.project = project
        this.platform = Platform.by(project)
    }

    void ide() {
        project.architectury.platformSetupLoomIde()
    }

    void common() {
        def projects = project.rootProject.subprojects.findAll { it != project }
        def platforms = projects.collect(it -> it.properties.get("loom.platform"))
        project.architectury.common(platforms) {
            // map neoforge to forge
            it.platformPackage "neoforge", "forge"
        }
        // disable common test task.
        project.tasks["runClientTest"].enabled = false
        project.tasks["runServerTest"].enabled = false
        //
        CocoonPlugin.commonProject = project
        setupLoom()
    }

    void fabric(Action<Settings> action = {}) {
        var settings = new Settings()
        action.execute(settings)
        setupLoom()
        extendsFrom(settings)
        project.architectury.fabric(settings.task)
    }

    void forge(Action<Settings> action = {}) {
        var settings = new Settings()
        settings.platformPackage = "forge"
        action.execute(settings)
        setupLoom()
        extendsFrom(settings)
        if (platform == Platform.FORGE) {
            project.architectury.forge(settings.task)
            project.loom.forge {
                it.convertAccessWideners = true
            }
        } else {
            project.architectury.neoForge(settings.task)
            project.loom.neoForge {
                // it.accessTransformers += []
            }
            project.remapJar {
                it.atAccessWideners.add project.loom.accessWidenerPath.get().asFile.name
            }
            project.dependencies.extensions.add("forge") {
                // we need map forge dependencies to neo forge dependencies.
                project.dependencies.neoForge it.replaceFirst(/net.minecraftforge:forge:/, /net.neoforged:neoforge:/)
            }
        }
    }

    void injectables() {
        compileOnlyMode = false
    }

    Project getCommonProject() {
        return CocoonPlugin.commonProject
    }

    private void setupLoom() {
        if (compileOnlyMode) {
            project.architectury.compileOnly()
        }
    }

    private void extendsFrom(Settings settings) {
        if (commonProject == null) {
            return
        }
        // inherit access widener setting from common.
        project.loom.accessWidenerPath = commonProject.loom.accessWidenerPath

        // inherit common configuration
        project.configurations.create("common") {
            project.configurations.maybeCreate("compileClasspath").extendsFrom(it)
            project.configurations.maybeCreate("runtimeClasspath").extendsFrom(it)
            project.configurations.maybeCreate("testCompileClasspath").extendsFrom(it)
            project.configurations.maybeCreate("testRuntimeClasspath").extendsFrom(it)
            // to use injectable mod, we need add the developmentFabric/developmentForge into source set.
            if (!compileOnlyMode) {
                project.configurations.maybeCreate("development" + platform.name).extendsFrom(it)
            }
        }
        // don't use shadow from the shadow plugin because we don't want IDEA to index this.
        project.configurations.create("shadowCommon")

        // to use compile only mode, we need add the source into loom mods.
        if (compileOnlyMode) {
            project.loom.mods {
                var settings1 = it.maybeCreate("main")
                settings1.sourceSet project.sourceSets.main
                settings1.sourceSet commonProject.sourceSets.main
            }
        }

        // inherit dependency the common classes.
        project.dependencies {
            it.common(it.project(path: commonProject.path, configuration: "namedElements")) {
                it.transitive false
            }
            it.shadowCommon(it.project(path: commonProject.path, configuration: "transformProduction" + platform.name)) {
                it.transitive false
            }
        }

        project.shadowJar {
            it.configurations = [project.configurations.shadowCommon]
            it.archiveClassifier.set("dev-shadow")
        }
        project.remapJar {
            it.injectAccessWidener = true
            it.inputFile.set project.shadowJar.archiveFile
            it.dependsOn project.shadowJar
            it.archiveClassifier.set(null)
        }

        project.jar {
            it.archiveClassifier.set("dev")
        }

        project.sourcesJar {
            def commonSources = commonProject.sourcesJar
            it.dependsOn commonSources
            it.from commonSources.archiveFile.map { project.zipTree(it) }
        }

        // inherit compile dependency from the common test classes.
        project.sourceSets.test.compileClasspath += commonProject.sourceSets.test.output

        project.tasks.named("runClientTest") {
            it.dependsOn commonProject.tasks.named("compileTestJava")
            it.dependsOn project.tasks.named("compileTestJava")
        }
        project.tasks.named("runServerTest") {
            it.dependsOn commonProject.tasks.named("compileTestJava")
            it.dependsOn project.tasks.named("compileTestJava")
        }

        project.components.java {
            it.withVariantsFromConfiguration(project.configurations.shadowRuntimeElements) {
                skip()
            }
        }
    }

    static class Settings {

        String platformPackage

        private Action getTask() {
            def settings = this
            return {
                it.platformPackage = settings.platformPackage
            }
        }
    }

    static enum Platform {
        FABRIC("Fabric"),
        FORGE("Forge"),
        NEOFORGE("NeoForge"),
        UNKNOWN("Common");

        final String name;

        Platform(String name) {
            this.name = name;
        }

        static Platform by(Project project) {
            switch (project.properties.get("loom.platform")) {
                case "fabric": return FABRIC
                case "forge": return FORGE
                case "neoforge": return NEOFORGE
            }
            return UNKNOWN
        }
    }
}