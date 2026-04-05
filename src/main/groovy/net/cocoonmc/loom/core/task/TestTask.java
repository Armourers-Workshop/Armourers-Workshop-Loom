package net.cocoonmc.loom.core.task;

import net.cocoonmc.loom.CocoonPlugin;
import net.cocoonmc.loom.core.TestServer;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.process.internal.DefaultJavaExecSpec;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

@CacheableTask
public abstract class TestTask extends JavaExec {

    @Input
    public abstract ListProperty<String> getTestPackages();

    @Input
    public abstract ListProperty<String> getTestClasses();

    public TestTask() {
        setGroup("loom");
        setDescription("Starts the " + getType().toLowerCase() + " test with run configuration");
        setDependsOn(getProxyTask().getDependsOn());
    }

    public TestTask selectClass(String... classes) {
        getTestClasses().addAll(classes);
        return this;
    }

    public TestTask selectPackage(String... packages) {
        getTestPackages().addAll(packages);
        return this;
    }

    @Override
    public void exec() {
        try (var server = new TestServer(0)) {
            // prepares the Java execution specification for running tests with the LoomTestAgent.
            var execSpec = getObjectFactory().newInstance(DefaultJavaExecSpec.class);
            copyTo(execSpec);
            execSpec.jvmArgs(String.format("--address=%s:%d", server.getAddress(), server.getPort()));
            execSpec.classpath(getRuntimeClasspath(getProject()));
            if (getCommonProject() != null) {
                execSpec.classpath(getRuntimeClasspath(getCommonProject()));
            }
            if (!execSpec.getMainClass().isPresent()) {
                execSpec.getMainClass().set("net.cocoonmc.loom.core.agent.TestAgent");
                execSpec.classpath(getClass().getProtectionDomain().getCodeSource().getLocation());
            }
            execSpec.systemProperty("junit.dli.task.name", getName());
            execSpec.systemProperty("junit.dli.task.type", getType().toLowerCase());
            execSpec.systemProperty("junit.dli.task.minecraft", getProject().findProperty("minecraft_version"));
            execSpec.systemProperty("junit.dli.task.minecraft.int", getProject().findProperty("minecraft_version_number"));

            // copy fabric/forge/common project run resources.
            for (var project : List.of(getProject(), getCommonProject())) {
                var sourceSets = ((SourceSetContainer) project.getProperties().get("sourceSets")).getAt("test");
                for (var resources : sourceSets.getResources().getSrcDirs()) {
                    for (var resource : project.fileTree(new File(resources, "run")).getFiles()) {
                        var target = getProject().file(resource.getPath().substring(resources.getPath().length() + 1));
                        if (!target.exists()) {
                            target.getParentFile().mkdirs();
                            Files.copy(resource.toPath(), target.toPath());
                        }
                    }
                }
            }

            // we need to wait (30s) for the host process to connect.
            var thread = Thread.currentThread();
            server.accept(this::handle, 30000, thread::interrupt);

            // configures the proxy task with generated configuration and execute its.
            var proxyTask = getProxyTask();
            var jvmArgs = new ArrayList<>(proxyTask.getJvmArgs());
            proxyTask.jvmArgs("-Djunit.dli.config=" + getConfigFile(execSpec).getPath());
            //proxyTask.jvmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5009");
            for (var action : proxyTask.getActions()) {
                action.execute(proxyTask);
            }
            proxyTask.setJvmArgs(jvmArgs);
        } catch (Exception e) {
            throw new GradleException(e.getMessage());
        }
    }

    protected void handle(TestServer.Controller controller) throws IOException {
        // run test with all selectors.
        controller.setLogger(getLogger());
        var results = controller.run(getTestClasses().get(), getTestPackages().get());
        results.printTo(new PrintWriter(System.out));
        results.printFailuresTo(new PrintWriter(System.out));
        controller.exit((int) results.getTotalFailureCount());
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
        var jvmArgs = new ArrayList<>(spec.getJvmArgs());
        spec.getSystemProperties().forEach((key, value) -> {
            var encodedValue = URLEncoder.encode(Objects.requireNonNullElse(value, "").toString(), StandardCharsets.UTF_8);
            jvmArgs.add(String.format("-D%s=%s", key, encodedValue));
        });

        var properties = new Properties();
        properties.put("junit.dli.main.class", spec.getMainClass().get());
        properties.put("junit.dli.classpath", String.join(File.pathSeparator, spec.getClasspath().getFiles().stream().map(File::getPath).toList()));
        properties.put("junit.dli.args", String.join(" ", jvmArgs));
        properties.storeToXML(new FileOutputStream(configFile), null);
        return configFile;
    }

    protected FileCollection getRuntimeClasspath(Project project) {
        var sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
        return sourceSets.getAt("test").getRuntimeClasspath();
    }
}
