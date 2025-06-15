package moe.plushie.armourers_workshop.loom

import moe.plushie.armourers_workshop.loom.impl.LoomTestServer
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.process.internal.DefaultJavaExecSpec

class LoomTestPlatformExt {

    public final Project project

    LoomTestPlatformExt(Project project) {
        this.project = project
    }

    void runClient(Action<TestSettings> action) {
        var task = project.tasks["runClient"] as JavaExec
        var settings = new TestSettings(project)
        action.execute(settings)
        inject(task, settings)
    }

    void runServer(Action<TestSettings> action) {
        var task = project.tasks["runServer"] as JavaExec
        var settings = new TestSettings(project)
        action.execute(settings)
        inject(task, settings)
    }

    private void inject(JavaExec runTask, TestSettings settings) {
        // the test task is enable?
        if (!settings.getEnabled()) {
            return
        }
        // setup the task depends.
        var testTask = project.tasks["test"] as Test
        var testServer = new LoomTestServer()
        testTask.dependsOn(runTask)
        // setup the junit config.
        var configFile = project.file(".gradle/junit/${runTask.name}.xml")
        configFile.parentFile.mkdirs()
        runTask.jvmArgs("-Djunit.dli.config=${configFile.path}")
        // generate config file before run.
        runTask.doFirst {
            // set the default value
            if (settings.mainClass.getOrNull() == null) {
                settings.mainClass.set("moe.plushie.armourers_workshop.loom.agent.LoomTestAgent")
                settings.classpath(this.class.protectionDomain.codeSource.location)
            }
            // generate startup parameters
            var address = testServer.address
            var port = testServer.port
            settings.jvmArgs("--address=$address:$port");
            var properties = new Properties()
            properties.put("junit.dli.main.class", settings.mainClass.get())
            properties.put("junit.dli.classpath", String.join(File.pathSeparator, settings.classpath.collect { it.path }))
            properties.put("junit.dli.args", String.join(' ', settings.jvmArgs))
            configFile.delete()
            properties.storeToXML(configFile.newOutputStream(), null)
            // open the test socket.
            testServer.open {
                var failures = 0
                if (!settings.testClasses.isEmpty()) {
                    var result = it.testClasses(settings.testClasses)
                    println(result)
                    failures += result.failures.size()
                }
                if (!settings.testPackages.isEmpty()) {
                    var result = it.testPackage(settings.testPackages)
                    println(result)
                    failures += result.failures.size()
                }
                it.exit(failures)
            }
        }
        runTask.doLast {
            testServer.close()
        }
    }


    static class TestSettings {

        private final Property<Boolean> enabled
        private final List<String> testPackages = new ArrayList<>()
        private final List<String> testClasses = new ArrayList<>()
        private final DefaultJavaExecSpec javaExecSpec

        TestSettings(Project project) {
            this.enabled = project.objects.property(Boolean.class)
            this.javaExecSpec = project.objects.newInstance(DefaultJavaExecSpec.class, new Object[0])
            // add common and sub project runtime classpath.
            this.classpath(project.sourceSets.test.runtimeClasspath)
            if (CocoonPluginExt.commonProject != null) {
                this.classpath(CocoonPluginExt.commonProject.sourceSets.test.runtimeClasspath)
            }
        }

        void selectClass(String... classes) {
            testClasses.addAll(classes)
        }

        void selectPackage(String... packages) {
            testPackages.addAll(packages)
        }

        Property<String> getMainClass() {
            return javaExecSpec.getMainClass()
        }

        TestSettings setClasspath(FileCollection classpath) {
            javaExecSpec.setClasspath(classpath)
            return this
        }

        TestSettings classpath(Object... paths) {
            javaExecSpec.classpath(paths)
            return this
        }

        FileCollection getClasspath() {
            return javaExecSpec.getClasspath()
        }


        List<String> getJvmArgs() {
            return javaExecSpec.getJvmArguments().getOrNull()
        }

        void setJvmArgs(List<String> arguments) {
            javaExecSpec.getJvmArguments().empty()
            jvmArgs(arguments)
        }

        void setJvmArgs(Iterable<?> arguments) {
            javaExecSpec.getJvmArguments().empty()
            jvmArgs(arguments)
        }

        TestSettings jvmArgs(Iterable<?> arguments) {
            for (var arg : arguments) {
                javaExecSpec.getJvmArguments().add(arg.toString())
            }
            javaExecSpec.checkDebugConfiguration(arguments);
            return this
        }

        TestSettings jvmArgs(Object... arguments) {
            jvmArgs(Arrays.asList(arguments))
            return this
        }

        void setEnabled(boolean newValue) {
            enabled.set(newValue)
        }

        boolean getEnabled() {
            return enabled.getOrElse(true)
        }
    }
}
