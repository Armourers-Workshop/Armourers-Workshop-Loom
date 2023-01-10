package moe.plushie.armourers_workshop.loom

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class SignJarTask extends DefaultTask {

    @TaskAction
    void main() {
        def remapJar = project.tasks["remapJar"]
        def store = System.getenv("AW_SIGN_STORE")
        if (store == null || store.isEmpty()) {
            didWork = false
            return
        }
        def tmp = File.createTempFile("aw2-store-", ".jks")
        tmp.bytes = store.decodeBase64()
        ant.signjar(destDir: remapJar.destinationDir,
                jar: remapJar.archivePath,
                alias: System.getenv("AW_SIGN_ALIAS"),
                keypass: System.getenv("AW_SIGN_KEY_PASS"),
                storepass: System.getenv("AW_SIGN_STORE_PASS"),
                keystore: tmp.path,
                storetype: "jks")
        tmp.delete()
    }
}
