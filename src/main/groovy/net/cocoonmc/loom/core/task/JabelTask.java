package net.cocoonmc.loom.core.task;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.work.DisableCachingByDefault;

import java.util.List;
import java.util.Objects;

@DisableCachingByDefault(because = "This task will configure the project properties and dependencies for Jabel integration.")
public abstract class JabelTask extends DefaultTask implements Action<Object> {

    @Input
    @Optional
    public abstract Property<String> getApi();

    @Input
    @Optional
    public abstract Property<String> getVersion();

    public JabelTask() {
        // this feature is turned off by default.
        setEnabled(false);
    }

    @Override
    public void execute(Object arguments) {
        // ignore ide and the user disable.
        if (!isEnabled()) {
            setDidWork(false);
            return;
        }
        getLogger().info("Applying Jabel Integration.");

        // add downgrade plugin maven repo.
        getProject().getRepositories().maven(it -> it.setUrl(resolveFileRepository()));

        // add downgrade plugin in the compile time.
        getProject().getDependencies().add("annotationProcessor", resolveFileDependency());
        getProject().getDependencies().add("testAnnotationProcessor", resolveFileDependency());

        // fix compatibility level to java 8 in mixin json.
        getProject().getTasks().named("processResources", ProcessResources.class).configure(task -> {
            task.getInputs().property(getName() + ".release", 8);
            task.filesMatching("*-mixins.json", details -> {
                details.filter(it -> it.replaceAll("(\"compatibilityLevel\"\\s*:\\s*\")(JAVA_\\d+)(\")", "$1JAVA_8$3"));
            });
        });

        // setup the java compile task.
        getProject().getTasks().withType(JavaCompile.class).configureEach(task -> {
            //task.getOptions().setEncoding("UTF-8");
            task.getOptions().getRelease().set(8);

            // Needed to get access to internal compiler classes
            task.getOptions().setFork(true);
            task.getOptions().getForkOptions().getJvmArgs().addAll(getExtraJvmArgs());
        });
    }

    @Internal
    public List<String> getExtraJvmArgs() {
        return List.of(
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
        );
    }

    @Override
    public boolean isEnabled() {
        var runOnIDE = Objects.equals(System.getProperty("idea.active"), "true");
        return super.isEnabled() && !runOnIDE;
    }

    private Object resolveFileDependency() {
        var api = getApi().getOrElse("com.xpdustry:jabel");
        var version = getVersion().getOrElse("1.1.0");
        return api + ":" + version;
    }

    private Object resolveFileRepository() {
        return "https://maven.xpdustry.com/releases/";
    }
}
