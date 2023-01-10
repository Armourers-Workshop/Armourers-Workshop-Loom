package moe.plushie.armourers_workshop.loom

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction

class PackConfigTask extends DefaultTask {

    private def allFiles = null

    @OutputFiles
    def getOutputFiles() {
        // we don't care about the exact call timing.
        if (allFiles == null) {
            allFiles = eval()
        }
        return project.files(allFiles.get("common") + allFiles.get(project.name)).getAsFileTree()
    }

    def eval() {
        def allFiles = [:]
        project.rootProject.allprojects.forEach {
            allFiles.put it.name, it.sourceSets.collect { it.resources }
        }
        logger.info("Evaluating pack for project ':{}': {}", project.name, allFiles)
        return allFiles
    }

    def attach() {
        if (project.name == "common") {
            return
        }
        project.tasks["processResources"].doLast {
            def resources = ""
            // we need to get a list of all the files in the jar,
            // but java does not provide such an api.
            // so we need to generate the resources list at build.
            outputFiles.visit {
                if (!it.isDirectory()) {
                    if (!resources.isEmpty()) {
                        resources += "\n"
                    }
                    resources += it.relativePath
                }
            }
            // if the file already exists, we need to check if it is available.
            def file = new File(project.sourceSets.main.output.resourcesDir, "pack.dat")
            if (file.exists() && file.text == resources) {
                setDidWork(false)
                return
            }
            file.delete()
            file.parentFile.mkdirs()
            file.write(resources)
        }
    }

    @TaskAction
    def main() {
    }
}
