package net.cocoonmc.loom.runtime.setup

import net.cocoonmc.loom.CocoonPlugin
import net.cocoonmc.loom.api.CocoonExtension
import net.cocoonmc.loom.api.CocoonSettings
import net.cocoonmc.loom.core.Platform
import net.cocoonmc.loom.runtime.CocoonExtensionImpl
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency

class PlatformSetup implements Action<CocoonSettings> {

    private final Project project
    private final CocoonExtensionImpl cocoon
    private final Platform platform

    PlatformSetup(Project project, Platform platform) {
        this.project = project
        this.cocoon = project.extensions.getByType(CocoonExtension) as CocoonExtensionImpl
        this.platform = platform
    }

    @Override
    void execute(CocoonSettings settings) {
        // configure the loom ide for the  platform.
        cocoon.architectury.platformSetupLoomIde()

        // configure the architectury minecraft version.
        cocoon.architectury.minecraft = cocoon.minecraft.get()

        // link the access widener to the common project.
        settings.accessWidenerPath.convention(commonProject.loom.accessWidenerPath)
        cocoon.loom.accessWidenerPath.convention(settings.accessWidenerPath)

        // call the details actions.
        detailsAction.execute(settings)

        // call the external actions.
        externalAction.execute(settings)

        // switch to the compile only mode if needs.
        if (cocoon.compileOnly.get()) {
            cocoon.architectury.compileOnly()
        }
    }

    private Project getCommonProject() {
        return CocoonPlugin.commonProject
    }

    private Action<CocoonSettings> getDetailsAction() {
        switch (platform) {
            case Platform.FORGE:
                return new LegacyForgeDetails()

            case Platform.NEOFORGE:
                return new NeoForgeDetails()

            default:
                return new FabricDetails()
        }
    }

    private Action<Object> getExternalAction() {
        if (cocoon.disableObfuscation) {
            return new WithoutObfuscationExternal()
        }
        return new ObfuscationExternal()
    }


    private Object getCommonDependency(Map<String, ?> notation) {
        var values = Map.of("path", commonProject.path)
        var dep = project.dependencies.project(values + notation) as ModuleDependency
        dep.setTransitive(true)
        return dep
    }

    private class FabricDetails implements Action<CocoonSettings> {

        @Override
        void execute(CocoonSettings settings) {
            // configure the fabric project dependencies.
            project.dependencies.add("modApi", cocoon.resolveApiDependency(settings))
            project.dependencies.add("modImplementation", cocoon.resolveLoaderDependency(settings))

            cocoon.architectury.fabric {
//                it.platformPackage = "fabric"
            }

            // in 1.16.5 we need to force use a fabric loader version.
            if (cocoon.minecraftNumber < 180000) {
                forceLoadDependency(settings)
            }
        }

        private void forceLoadDependency(CocoonSettings settings) {
            // force set the dependency version.
            project.configurations.configureEach {
                it.resolutionStrategy.force cocoon.resolveLoaderDependency(settings)
            }
        }
    }

    private class NeoForgeDetails implements Action<CocoonSettings> {

        @Override
        void execute(CocoonSettings settings) {
            // configure the forge project dependencies.
            project.dependencies.add("neoForge", cocoon.resolveApiDependency(settings))

            cocoon.architectury.neoForge {
                it.platformPackage = "forge"
            }
            cocoon.loom.neoForge {
                // it.accessTransformers += []
            }

            project.tasks.findByName("remapJar")?.configure {
                var accessWidener = settings.accessWidenerPath.asFile.get()
                it.atAccessWideners.add(accessWidener.name)
            }
        }
    }

    private class LegacyForgeDetails implements Action<CocoonSettings> {

        @Override
        void execute(CocoonSettings settings) {
            // configure the forge project dependencies.
            project.dependencies.add("forge", cocoon.resolveApiDependency(settings))

            cocoon.architectury.forge {
                it.platformPackage = "forge"
            }
            cocoon.loom.forge {
                it.convertAccessWideners.set(true)
            }
        }
    }

    private class ObfuscationExternal implements Action<Object> {

        @Override
        void execute(Object object) {
            setupSourceSets()
            setupConfigurations()
            setupDependencies()
            setupArchiveTasks()
            setupArchiveElements()
            setupTestTasks()
        }

        void setupConfigurations() {
            // inherit common configuration
            project.configurations.create("common") {
                project.configurations.maybeCreate("compileClasspath").extendsFrom(it)
                project.configurations.maybeCreate("runtimeClasspath").extendsFrom(it)
                project.configurations.maybeCreate("testCompileClasspath").extendsFrom(it)
                project.configurations.maybeCreate("testRuntimeClasspath").extendsFrom(it)
                // to use injectable mod, we need add the developmentFabric/developmentForge into source set.
                if (!cocoon.compileOnly.get()) {
                    project.configurations.maybeCreate("development" + platform.name).extendsFrom(it)
                }
            }
            // don't use shadow from the shadow plugin because we don't want IDEA to index this.
            project.configurations.create("shadowCommon")
        }

        void setupSourceSets() {
            // to use compile only mode, we need add the source into loom mods.
            if (cocoon.compileOnly.get()) {
                cocoon.loom.mods {
                    var settings1 = it.maybeCreate("main")
                    settings1.sourceSet project.sourceSets.main
                    settings1.sourceSet commonProject.sourceSets.main
                }
            }
        }

        void setupDependencies() {
            // inherit dependency the common classes.
            project.dependencies.add("common", getCommonDependency(configuration: "namedElements"))
            project.dependencies.add("shadowCommon", getCommonDependency(configuration: "transformProduction" + platform.name))
        }

        void setupArchiveTasks() {
            project.jar {
                it.archiveClassifier = "dev"
            }

            project.shadowJar {
                it.configurations = [project.configurations.shadowCommon]
                it.archiveClassifier = "dev-shadow"
            }

            project.remapJar {
                it.dependsOn project.shadowJar

                it.archiveClassifier = null
                it.injectAccessWidener = true
                it.inputFile = project.shadowJar.archiveFile
            }

            project.sourcesJar {
                def sourcesTask = commonProject.sourcesJar
                it.dependsOn sourcesTask
                it.from sourcesTask.archiveFile.map { project.zipTree(it) }
            }

            project.signJar {
                it.add project.remapJar
            }
        }

        void setupArchiveElements() {
            project.components.java {
                it.withVariantsFromConfiguration(project.configurations.shadowRuntimeElements) {
                    skip()
                }
            }
        }

        void setupTestTasks() {
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
        }

//        void setupJabelTasks() {
//            var jabel = project.jabel as DefaultTask
//            if (!jabel.enabled || cocoon.runOnIDE) {
//                return
//            }
//            println("setup jabel")
//            // force the java version to 8.
//            project.rootProject.java_version = 8
//
//            // add downgrade plugin in the compile time.
//            project.dependencies.add("annotationProcessor", "com.pkware.jabel:jabel-javac-plugin:1.0.1-1")
//            project.dependencies.add("testAnnotationProcessor", "com.pkware.jabel:jabel-javac-plugin:1.0.1-1")
//
//            project.processResources {
//                // fix compatibility level to java 8 in mixin json.
//                it.inputs.property('compatibilityLevel', 8)
//                it.filesMatching("*-mixins.json") {
//                    it.filter {
//                        it.replaceAll(/("compatibilityLevel\"\s*:\s*")(JAVA_\d+)(")/, /$1JAVA_8$3/)
//                    }
//                }
//            }
//        }
    }

    private class WithoutObfuscationExternal extends ObfuscationExternal {

        @Override
        void setupDependencies() {
            // inherit dependency the common classes.
            project.dependencies.add("common", getCommonDependency(Map.of()))
            project.dependencies.add("shadowCommon", getCommonDependency(configuration: "transformProduction" + platform.name))
        }

        @Override
        void setupArchiveTasks() {
            project.jar {
                it.archiveClassifier = "dev"
            }

            project.shadowJar {
                it.dependsOn project.jar

                it.from project.zipTree(project.jar.archiveFile)
                it.mainSpec.sourcePaths.clear()

                it.configurations = [project.configurations.shadowCommon]
                it.archiveClassifier = null // write to jar
            }

            project.sourcesJar {
                def sourcesTask = commonProject.sourcesJar
                it.dependsOn sourcesTask
                it.from sourcesTask.archiveFile.map { project.zipTree(it) }
            }

            project.signJar {
                it.add project.shadowJar
            }
        }

        @Override
        void setupArchiveElements() {
            //super.setupArchiveElements()
        }
    }
}
