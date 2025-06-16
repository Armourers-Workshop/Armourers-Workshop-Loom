package moe.plushie.armourers_workshop.loom.core.task;

import moe.plushie.armourers_workshop.loom.CocoonPlugin;
import moe.plushie.armourers_workshop.loom.core.LoomTestServer;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.process.internal.DefaultJavaExecSpec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Properties;

@SuppressWarnings("unused")
public abstract class LoomTestTask extends JavaExec {

    @Input
    public abstract ListProperty<String> getTestPackages();

    @Input
    public abstract ListProperty<String> getTestClasses();

    public LoomTestTask() {
        setGroup("loom");
        setDescription("Starts the " + getType().toLowerCase() + " test with run configuration");
        setDependsOn(getProxyTask().getDependsOn());
        dependsOn("test");
    }

    public LoomTestTask selectClass(String... classes) {
        getTestClasses().addAll(classes);
        return this;
    }

    public LoomTestTask selectPackage(String... packages) {
        getTestPackages().addAll(packages);
        return this;
    }

    @Override
    public void exec() {
        var thread = Thread.currentThread();
        try (var server = new LoomTestServer(0)) {
            // we need to wait (30s) for the host process to connect.
            server.accept(this::handle, 30000, thread::interrupt);

            // prepares the Java execution specification for running tests with the LoomTestAgent.
            var execSpec = getObjectFactory().newInstance(DefaultJavaExecSpec.class);
            copyTo(execSpec);
            execSpec.jvmArgs(String.format("--address=%s:%d", server.getAddress(), server.getPort()));
            execSpec.classpath(getRuntimeClasspath(getProject()));
            if (getCommonProject() != null) {
                execSpec.classpath(getRuntimeClasspath(getCommonProject()));
            }
            if (!execSpec.getMainClass().isPresent()) {
                execSpec.getMainClass().set("moe.plushie.armourers_workshop.loom.core.agent.LoomTestAgent");
                execSpec.classpath(getClass().getProtectionDomain().getCodeSource().getLocation());
            }

            // configures the proxy task with generated configuration and executes its .
            var proxyTask = getProxyTask();
            var jvmArgs = new ArrayList<>(proxyTask.getJvmArgs());
            proxyTask.jvmArgs("-Djunit.dli.config=" + getConfigFile(execSpec).getPath());
            for (var action : proxyTask.getActions()) {
                action.execute(proxyTask);
            }
            proxyTask.setJvmArgs(jvmArgs);

        } catch (Exception e) {
            e.printStackTrace();
            throw new GradleException(e.getMessage(), e);
        }
    }

    protected void handle(LoomTestServer.Controller controller) throws IOException {
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

    @Internal
    public String getType() {
        return getName().replaceAll("run(.+)Test", "$1");
    }

    @Internal
    public JavaExec getProxyTask() {
        return (JavaExec) getProject().getTasks().getAt("run" + getType());
    }

    @Internal
    public Project getCommonProject() {
        return CocoonPlugin.commonProject;
    }

    protected File getConfigFile(DefaultJavaExecSpec spec) throws IOException {
        // set up the junit config.
        var configFile = getProject().file(".gradle/junit/" + getName() + ".xml");
        if (configFile.exists()) {
            configFile.delete();
        } else {
            configFile.getParentFile().mkdirs();
        }
        var properties = new Properties();
        properties.put("junit.dli.main.class", spec.getMainClass().get());
        properties.put("junit.dli.classpath", String.join(File.pathSeparator, spec.getClasspath().getFiles().stream().map(File::getPath).toList()));
        properties.put("junit.dli.args", String.join(" ", Objects.requireNonNull(spec.getJvmArgs())));
        properties.storeToXML(new FileOutputStream(configFile), null);
        return configFile;
    }

    protected FileCollection getRuntimeClasspath(Project project) {
        var sourceSets = (SourceSetContainer) project.getProperties().get("sourceSets");
        return sourceSets.getAt("test").getRuntimeClasspath();
    }
}
