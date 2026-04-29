package net.cocoonmc.loom.core.task;

import com.google.gson.JsonObject;
import groovy.lang.Closure;
import net.cocoonmc.loom.api.CocoonExtension;
import net.cocoonmc.loom.runtime.CocoonExtensionImpl;
import net.fabricmc.loom.util.ZipUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

import java.io.IOException;
import java.util.Map;

@SuppressWarnings("NullableProblems")
public abstract class InjectJarTask extends DefaultTask {

    @Input
    @Optional
    public abstract Property<Boolean> getInjectAccessWidener();

    @Internal
    public CocoonExtensionImpl getCocoon() {
        return (CocoonExtensionImpl) getProject().getExtensions().getByType(CocoonExtension.class);
    }

    @Override
    public Task configure(Closure closure) {
        var task = super.configure(closure);
        apply();
        return task;
    }

    private void apply() {
        // ignore when the inject is disabled!
        if (!getInjectAccessWidener().getOrElse(false)) {
            return;
        }
        var cocoon = getCocoon();
        var accessWidenerPath = cocoon.getLoom().getAccessWidenerPath().getOrNull();
        if (accessWidenerPath == null) {
            return;
        }
        var tasks = getTaskDependencies().getDependencies(this);
        var accessWidener = accessWidenerPath.getAsFile().getName();
        switch (cocoon.getPlatform()) {
            case NEOFORGE -> {
                // inject the convert task into dependencies.
                cocoon.getLoom().neoForge(it -> tasks.forEach(task -> {
                    // Converting AW to AT in unobfuscated
                    // NeoForge 26.1+ projects.
                    var provider = getProject().getTasks().named(task.getName());
                    //noinspection unchecked,rawtypes,UnstableApiUsage
                    it.convertAccessWideners((TaskProvider) provider, accessWidener);
                }));
            }
            case FABRIC -> {
                // inject the replace task into dependencies.
                tasks.forEach(it -> it.doLast((task) -> {
                    try {
                        var outputFile = ((Jar) task).getArchiveFile().get().getAsFile().toPath();
                        ZipUtils.transformJson(JsonObject.class, outputFile, Map.of("fabric.mod.json", json -> {
                            json.addProperty("accessWidener", accessWidener);
                            return json;
                        }));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));
            }
        }
    }
}
