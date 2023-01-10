package moe.plushie.armourers_workshop.loom

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction

class MixinConfigTask extends DefaultTask {

    private def allFiles = null

    @OutputFiles
    def getOutputFiles() {
        // we don't care about the exact call timing.
        if (allFiles == null) {
            allFiles = eval { it.name.endsWith("mixins.json") }
        }
        return project.files((allFiles.get("common") + allFiles.get(project.name)).sort())
    }

    def eval(filter) {
        def allFiles = [:]
        project.rootProject.allprojects.forEach {
            def sources = it.sourceSets.collect { it.allSource }
            allFiles.put it.name, sources.collectMany { it.findAll(filter) }
        }
        logger.info("Evaluating mixins for project ':{}': {}", project.name, allFiles)
        return allFiles
    }

    def attach() {
        if (project.name == "forge") {
            forge()
        }
        if (project.name == "fabric") {
            fabric()
        }
    }

    def forge() {
        // when we switch the minecraft, the mixin will changes,
        // so we need find all active mixins in the project,
        // and then add all mixins name to manifest.
        project.loom.forge { api ->
            outputFiles.forEach {
                api.mixinConfig it.name
            }
        }
    }

    def fabric() {
        // when we switch the minecraft, the mixin will changes,
        // so we need find all active mixins in the project,
        // and then add all mixins name to manifest.
        project.tasks["processResources"].filesMatching("fabric.mod.json") {
            def mixins = ""
            outputFiles.forEach {
                if (!mixins.isEmpty()) {
                    mixins += ", "
                }
                mixins += "\"${it.name}\""
            }
            replaceAll it, /(?sim)("mixins"\s*:\s*\[\s*)(.+?)(\s*\])/, /$1/ + mixins + /$3/
        }
    }

    @TaskAction
    def main() {
    }

    static def replaceAll(details, regex, value) {
        def results = ""
        details.filter {
            if (results.isEmpty()) {
                results += it
                return ""
            } else {
                results += "\n"
                results += it
                return null
            }
        }
        details.filter {
            results.replaceAll(regex, value)
        }
    }
}
