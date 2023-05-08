package moe.plushie.armourers_workshop.loom

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.AbstractArchiveTask

class SignJarTask extends DefaultTask {

    @TaskAction
    void main() {
        def remapJar = project.tasks["remapJar"]  as AbstractArchiveTask
        def store = System.getenv("AW_SIGN_STORE")
        if (store == null || store.isEmpty()) {
            didWork = false
            return
        }
        def target = remapJar.archiveFile.get().asFile
        def tmp = File.createTempFile("aw2-store-", ".jks")
        tmp.bytes = store.decodeBase64()
        ant.signjar(destDir: target.parent,
                jar: target,
                alias: System.getenv("AW_SIGN_ALIAS"),
                keypass: System.getenv("AW_SIGN_KEY_PASS"),
                storepass: System.getenv("AW_SIGN_STORE_PASS"),
                keystore: tmp.path,
                storetype: "jks")
        tmp.delete()
    }
}
