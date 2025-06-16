package moe.plushie.armourers_workshop.loom.task;

import moe.plushie.armourers_workshop.loom.CocoonPlugin;
import moe.plushie.armourers_workshop.loom.core.LoomTestServer;
import moe.plushie.armourers_workshop.loom.core.ThrowableAction;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSetContainer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

@SuppressWarnings("unused")
public abstract class LoomTestTask extends JavaExec {

    @Input
    public abstract ListProperty<String> getTestPackages();

    @Input
    public abstract ListProperty<String> getTestClasses();

    public LoomTestTask() throws IOException {
        var name = getName().replaceAll("run(.+)Test", "$1");
        setGroup("loom");
        setDescription("Starts the " + name.toLowerCase() + " test with run configuration");
        setup((JavaExec) getProject().getTasks().getAt("run" + name));
    }

    @Override
    public void exec() {
        // this is a dummy exec task.
        System.out.println("run " + this);
    }

    private void setup(JavaExec task) throws IOException {
        // setup the junit config.
        var server = new LoomTestServer(0);
        var configFile = getProject().file(".gradle/junit/" + getName() + ".xml");
        if (configFile.exists()) {
            configFile.delete();
        } else {
            configFile.getParentFile().mkdirs();
        }
        task.jvmArgs("-Djunit.dli.config=" + configFile.getPath());
        // setup the task depends.
        dependsOn(task);
        task.doFirst(safeCall(it -> {
            // setup the default arguments.
            jvmArgs(String.format("--address=%s:%d", server.getAddress(), server.getPort()));
            classpath(getRuntimeClasspath(getProject()));
            if (getCommonProject() != null) {
                classpath(getRuntimeClasspath(getCommonProject()));
            }
            if (getMainClass().getOrNull() == null) {
                getMainClass().set("moe.plushie.armourers_workshop.loom.agent.LoomTestAgent");
                classpath(getClass().getProtectionDomain().getCodeSource().getLocation());
            }
            // generate startup parameters
            var properties = new Properties();
            properties.put("junit.dli.main.class", getMainClass().get());
            properties.put("junit.dli.classpath", String.join(File.pathSeparator, getClasspath().getFiles().stream().map(File::getPath).toList()));
            properties.put("junit.dli.args", String.join(" ", getJvmArgs()));
            properties.storeToXML(new FileOutputStream(configFile), null);
            // listen the host process callback connection.
            server.accept(this::proccess);
        }));
        task.doLast(safeCall(it -> {
            // close the server when host process is terminated.
            server.close();
        }));
        server.setTimeoutHandler(it -> {
            // we can't abort executing task, so we need abort all task.
            System.exit(-1);
        });
    }

    private void proccess(LoomTestServer.Controller controller) throws IOException {
        var failures = 0;
        var classes = getTestClasses().get();
        if (!classes.isEmpty()) {
            var result = controller.testClasses(classes);
            System.out.println(result);
            failures += result.failures.size();
        }
        var packages = getTestPackages().get();
        if (!packages.isEmpty()) {
            var result = controller.testPackage(packages);
            System.out.println(result);
            failures += result.failures.size();
        }
        controller.exit(failures);
    }

    private FileCollection getRuntimeClasspath(Project project) {
        var sourceSets = (SourceSetContainer) project.getProperties().get("sourceSets");
        return sourceSets.getAt("test").getRuntimeClasspath();
    }

    @Internal
    public Project getCommonProject() {
        return CocoonPlugin.commonProject;
    }

    public LoomTestTask selectClass(String... classes) {
        getTestClasses().addAll(classes);
        return this;
    }

    public LoomTestTask selectPackage(String... packages) {
        getTestPackages().addAll(packages);
        return this;
    }

    private <T> Action<T> safeCall(ThrowableAction<T> action) {
        return it -> {
            try {
                action.execute(it);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
