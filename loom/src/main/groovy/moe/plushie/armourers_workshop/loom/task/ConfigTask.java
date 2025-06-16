package moe.plushie.armourers_workshop.loom.task;

import net.fabricmc.loom.extension.LoomGradleExtensionImpl;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;
import org.gradle.language.jvm.tasks.ProcessResources;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

@SuppressWarnings("unused")
public class ConfigTask extends DefaultTask {

    private LinkedHashMap<String, List<File>> allFiles = null;

    @OutputFiles
    public ConfigurableFileCollection getOutputFiles() {
        // we don't care about the exact call timing.
        if (allFiles == null) {
            allFiles = searchInAllProjects(file -> {
                return file.getName().endsWith("mixins.json");
            });
        }
        var results = new ArrayList<File>();
        results.addAll(allFiles.getOrDefault("common", Collections.emptyList()));
        results.addAll(allFiles.getOrDefault(getProject().getName(), Collections.emptyList()));
        results.sort(Comparator.naturalOrder());
        return getProject().files(results);
    }

    @TaskAction
    public void run() {
    }

    public void setup() {
        var platform = String.valueOf(getProject().getProperties().get("loom.platform"));
        switch (platform) {
            case "fabric" -> fabric();
            case "forge" -> forge();
            case "neoforge" -> neoforge();
        }
    }

    public void fabric() {
        // when we switch the minecraft, the mixin will changes,
        // so we need find all active mixins in the project,
        // and then add all mixins name to manifest.
        var processTask = (ProcessResources) getProject().getTasks().getByName("processResources");
        processTask.filesMatching("fabric.mod.json", details -> {
            var mixins = new StringBuilder();
            getOutputFiles().forEach(file -> {
                if (!mixins.isEmpty()) {
                    mixins.append(", ");
                }
                mixins.append("\"").append(file.getName()).append("\"");
            });
            replaceText(details, "(?sim)(\"mixins\"\\s*:\\s*\\[\\s*)(.+?)(\\s*\\])", "$1" + mixins + "$3");
        });
    }

    public void forge() {
        // when we switch the minecraft, the mixin will changes,
        // so we need find all active mixins in the project,
        // and then add all mixins name to manifest.
        var loomExtension = (LoomGradleExtensionImpl) getProject().findProperty("loom");
        loomExtension.forge(api -> {
            getOutputFiles().forEach(file -> {
                api.mixinConfig(file.getName());
            });
        });
    }

    public void neoforge() {
        // when we switch the minecraft, the mixin will changes,
        // so we need find all active mixins in the project,
        // and then add all mixins name to manifest.
        var processTask = (ProcessResources) getProject().getTasks().getByName("processResources");
        processTask.filesMatching("META-INF/mods.toml", details -> {
            var mixins = new StringBuilder("\n\n");
            getOutputFiles().forEach(file -> {
                mixins.append("[[mixins]]\n");
                mixins.append("config = \"").append(file.getName()).append("\"");
                mixins.append("\n\n");
            });
            replaceText(details, results -> {
                results = results.replaceAll("(?sim)(^\\s*modId\\s*=\\s*\")forge(\".+)", "$1neoforge$2");
                results = results.replaceAll("(?sim)(^\\s*mandatory\\s*=\\s*)true", "type = \"required\"");
                results = results.replaceAll("(?sim)(^\\s*mandatory\\s*=\\s*)false", "type = \"optional\"");
                return results + mixins;
            });
            // in versions 1.20.5, the `mods.toml` rename to `neoforge.mods.toml`
            var mcVersion = (Integer) getProject().findProperty("minecraft_version_number");
            if (mcVersion.intValue() >= 12005) {
                details.setName("neoforge.mods.toml");
            }
        });
    }

    private LinkedHashMap<String, List<File>> searchInAllProjects(Predicate<File> filter) {
        var allFiles = new LinkedHashMap<String, List<File>>();
        for (var project : getProject().getRootProject().getAllprojects()) {
            var files = new ArrayList<File>();
            var sourceSets = (SourceSetContainer) project.getProperties().get("sourceSets");
            for (var sourceSet : sourceSets) {
                for (var file : sourceSet.getAllSource().getFiles()) {
                    if (filter.test(file)) {
                        files.add(file);
                    }
                }
            }
            allFiles.put(project.getName(), files);
        }
        getLogger().info("Evaluating mixins for project ':{}': {}", getProject().getName(), allFiles);
        return allFiles;
    }

    // example: 1.18.2-SNAPSHOT) => 1.18.2 => 1|18|02 => 11802 - 1 => 11801
    public static int parseVersion(String version, int undefined) {
        // check the limiter offset.
        var offset = 0;
        if (version.startsWith("(") || version.endsWith(")")) {
            if (version.length() == 1) {
                return undefined;
            }
            offset = 1;
        }
        // remove limiter: 1.18.2-SNAPSHOT
        // remove -SNAPSHOT part: 1.18.2
        // split version part to major, minor, patch: 1, 18, 2
        var versions = version.replaceAll("[()\\[\\]]", "").split("-")[0].split("\\.");

        // combine the all version part: 11802
        // apply the limiter offset: 11801
        var major = Integer.parseInt(versions.length > 0 ? versions[0] : "0");
        var minor = Integer.parseInt(versions.length > 1 ? versions[1] : "0");
        var patch = Integer.parseInt(versions.length > 2 ? versions[2] : "0");
        return Integer.parseInt(String.format("%d%02d%02d", major, minor, patch)) - offset;
    }

    public static void replaceText(FileCopyDetails details, String regex, String replacement) {
        replaceText(details, it -> it.replaceAll(regex, replacement));
    }

    public static void replaceText(FileCopyDetails details, Function<String, String> transformer) {
        var results = new StringBuilder();
        details.filter(it -> {
            if (results.isEmpty()) {
                results.append(it);
                return "";
            } else {
                results.append("\n");
                results.append(it);
                return null;
            }
        });
        details.filter(it -> transformer.apply(results.toString()));
    }
}
