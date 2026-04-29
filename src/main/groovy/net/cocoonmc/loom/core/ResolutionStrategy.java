package net.cocoonmc.loom.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.gradle.GradleUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactSelectionDetails;
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.dsl.ModuleComponentSelectorParsers;

import java.util.regex.Pattern;

public class ResolutionStrategy {

    private static final Pattern SPLITTER = Pattern.compile("^([^:]+):([^:]+):(.+)$");

    private final Project project;
    private final Platform platform;

    public ResolutionStrategy(Project project, Platform platform) {
        this.project = project;
        this.platform = platform;
    }

    public void eachDependency(Action<? super DependencyResolveDetails> action) {
        // forward the resolution strategy to launch config.
        applyToConfig(action);

        // forward the resolution strategy to each configuration
        project.getConfigurations().configureEach(it -> it.getResolutionStrategy().eachDependency(action));
    }

    @SuppressWarnings("all")
    private void applyToConfig(Action<? super DependencyResolveDetails> action) {
        // search the dependency providers.
        var extension = LoomGradleExtension.get(project);
        if (extension.getDependencyProviders() == null) {
            GradleUtils.afterSuccessfulEvaluation(project, () -> applyToConfig(action));
            return;
        }
        // search the config in the forge userdev provider.
        if (platform.isForgeLike()) {
            var config = extension.getForgeUserdevProvider().getJson();
            applyToConfig(config, "modules", action);
            applyToConfig(config, "libraries", action);
        }
    }

    private void applyToConfig(JsonObject config, String name, Action<? super DependencyResolveDetails> action) {
        // search the dependency configurations by name.
        if (!(config.get(name) instanceof JsonArray entries)) {
            return;
        }
        // apply the entry to dependency configure.
        for (int i = 0; i < entries.size(); i++) {
            // parse the dependency info.
            var matcher = SPLITTER.matcher(entries.get(i).getAsString());
            if (!matcher.matches()) {
                continue;
            }
            // apply the dependency transform.
            var details = new DummyResolveDetails(matcher.group(1), matcher.group(2), matcher.group(3));
            action.execute(details);
            if (!details.isDirty()) {
                continue;
            }
            entries.set(i, new JsonPrimitive(details.getTarget().toString()));
        }
    }

    private static class DummyResolveDetails implements DependencyResolveDetails {

        private ModuleVersionSelector target;
        private final ModuleVersionSelector requested;

        public DummyResolveDetails(String group, String name, String version) {
            this.requested = new DefaultExternalModuleDependency(group, name, version);
            this.target = requested;
        }

        @Override
        public void useTarget(Object notation) {
            this.target = (ModuleVersionSelector) ModuleComponentSelectorParsers.parser("useTarget()").parseNotation(notation);
        }

        @Override
        public void useVersion(String version) {
            this.target = new DefaultExternalModuleDependency(requested.getGroup(), requested.getName(), version);
        }

        @Override
        public DummyResolveDetails because(String description) {
            return this;
        }

        @Override
        public DummyResolveDetails artifactSelection(Action<? super ArtifactSelectionDetails> configurationAction) {
            return this;
        }

        @Override
        public ModuleVersionSelector getTarget() {
            return this.target;
        }

        @Override
        public ModuleVersionSelector getRequested() {
            return this.requested;
        }

        public boolean isDirty() {
            return this.target != this.requested;
        }
    }
}
