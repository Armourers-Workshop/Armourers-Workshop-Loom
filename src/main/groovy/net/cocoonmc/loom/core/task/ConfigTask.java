package net.cocoonmc.loom.core.task;

import net.cocoonmc.loom.api.CocoonExtension;
import net.cocoonmc.loom.core.Platform;
import net.cocoonmc.loom.runtime.CocoonExtensionImpl;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

@DisableCachingByDefault(because = "This task does not produce any output files that can be cached, as it only searches for specific files and does not perform any operations that would benefit from caching.")
public class ConfigTask extends DefaultTask implements Action<Object> {

    private LinkedHashMap<String, List<File>> allFiles = null;

    @Internal
    public CocoonExtensionImpl getCocoon() {
        return (CocoonExtensionImpl) getProject().getExtensions().getByType(CocoonExtension.class);
    }

    @OutputFiles
    public ConfigurableFileCollection getOutputFiles() {
        // we don't care about the exact call timing.
        if (allFiles == null) {
            allFiles = searchInAllProjects(file -> file.getName().endsWith("mixins.json"));
        }
        var results = new ArrayList<File>();
        results.addAll(allFiles.getOrDefault("common", Collections.emptyList()));
        results.addAll(allFiles.getOrDefault(getProject().getName(), Collections.emptyList()));
        results.sort(Comparator.naturalOrder());
        return getProject().files(results);
    }

    @Override
    public void execute(Object arguments) {
        getLogger().info("Applying Mixin Config.");
        switch (getCocoon().getPlatform()) {
            case Platform.FABRIC -> fabric();
            case Platform.FORGE -> forge();
            case Platform.NEOFORGE -> neoforge();
        }
    }

    private void fabric() {
        // when we switch the minecraft, the mixin will changes,
        // so we need find all active mixins in the project,
        // and then add all mixins name to manifest.
        getProject().getTasks().named("processResources", ProcessResources.class).configure(task -> {
            task.getInputs().property(getName() + ".files", getOutputFiles().getFiles());
            task.filesMatching("fabric.mod.json", details -> {
                var mixins = new StringBuilder();
                getOutputFiles().forEach(file -> {
                    if (!mixins.isEmpty()) {
                        mixins.append(", ");
                    }
                    mixins.append("\"").append(file.getName()).append("\"");
                });
                transformText(details, results -> {
                    results = results.replaceAll("(?sim)(\"mixins\"\\s*:\\s*\\[\\s*)(.+?)(\\s*])", "$1" + mixins + "$3");
                    if (getCocoon().getMinecraftNumber() < 180000) {
                        results = results.replaceAll("(?sim)(\"depends\"\\s*:\\s*\\{\\s*)(.+?)(\"fabric-api\")(.+?)(\\s*})", "$1$2" + "\"fabric\"" + "$4$5");
                    }
                    return results;
                });
            });
        });
    }

    private void forge() {
        // when we switch the minecraft, the mixin will changes,
        // so we need find all active mixins in the project,
        // and then add all mixins name to manifest.
        getCocoon().getLoom().forge(api -> {
            getOutputFiles().forEach(file -> {
                api.mixinConfig(file.getName());
            });
        });
    }

    private void neoforge() {
        // when we switch the minecraft, the mixin will changes,
        // so we need find all active mixins in the project,
        // and then add all mixins name to manifest.
        getProject().getTasks().named("processResources", ProcessResources.class).configure(task -> {
            task.getInputs().property(getName() + ".files", getOutputFiles().getFiles());
            task.filesMatching("META-INF/mods.toml", details -> {
                var mixins = new StringBuilder("\n\n");
                getOutputFiles().forEach(file -> {
                    mixins.append("[[mixins]]\n");
                    mixins.append("config = \"").append(file.getName()).append("\"");
                    mixins.append("\n\n");
                });
                transformText(details, results -> {
                    results = results.replaceAll("(?sim)(^\\s*modId\\s*=\\s*\")forge(\".+)", "$1neoforge$2");
                    results = results.replaceAll("(?sim)(^\\s*mandatory\\s*=\\s*)true", "type = \"required\"");
                    results = results.replaceAll("(?sim)(^\\s*mandatory\\s*=\\s*)false", "type = \"optional\"");
                    return results + mixins;
                });
                // in versions 1.20.5, the `mods.toml` rename to `neoforge.mods.toml`
                if (getCocoon().getMinecraftNumber() >= 200500) {
                    details.setName("neoforge.mods.toml");
                }
            });
        });
    }

    private LinkedHashMap<String, List<File>> searchInAllProjects(Predicate<File> filter) {
        var allFiles = new LinkedHashMap<String, List<File>>();
        for (var project : getProject().getRootProject().getAllprojects()) {
            var sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            var sourceFiles = sourceSets.stream()
                    .flatMap(it -> it.getAllSource().getFiles().stream())
                    .filter(filter)
                    .toList();
            allFiles.put(project.getName(), sourceFiles);
        }
        getLogger().info("Evaluating mixins for project ':{}': {}", getProject().getName(), allFiles);
        return allFiles;
    }


    private void transformText(FileCopyDetails details, Function<String, String> transformer) {
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
