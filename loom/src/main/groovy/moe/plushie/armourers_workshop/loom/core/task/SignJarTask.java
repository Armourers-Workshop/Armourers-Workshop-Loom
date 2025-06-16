package moe.plushie.armourers_workshop.loom.core.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.Base64;
import java.util.LinkedHashMap;

@SuppressWarnings("unused")
public abstract class SignJarTask extends DefaultTask {

    @Input
    public abstract Property<String> getAlias();

    @Input
    public abstract Property<String> getKeyPass();

    @Input
    public abstract Property<String> getKeyStore();

    @Input
    public abstract Property<String> getKeyStorePass();

    public SignJarTask() {
        setGroup("build");
        setDescription("Signing a jar allows users to authenticate the publisher.");
    }

    @TaskAction
    public void exec() throws IOException {
        var remapJar = (AbstractArchiveTask) getProject().getTasks().getAt("remapJar");
        var store = getKeyStore().getOrNull();
        if (store == null || store.isEmpty()) {
            setDidWork(false);
            return;
        }
        var target = remapJar.getArchiveFile().get().getAsFile();
        var tmp = File.createTempFile("aw2-store-", ".jks");
        Files.write(tmp.toPath(), Base64.getDecoder().decode(store));
        var args = new LinkedHashMap<String, Serializable>();
        args.put("destDir", target.getParent());
        args.put("jar", target);
        args.put("alias", getAlias().getOrNull());
        args.put("keystore", tmp.getPath());
        args.put("keypass", getKeyPass().getOrNull());
        args.put("storetype", "jks");
        args.put("storepass", getKeyStorePass().getOrNull());
        getAnt().invokeMethod("signjar", args);
        tmp.delete();
    }
}
