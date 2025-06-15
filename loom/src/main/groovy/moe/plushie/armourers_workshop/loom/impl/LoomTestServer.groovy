package moe.plushie.armourers_workshop.loom.impl

import moe.plushie.armourers_workshop.loom.agent.LoomTestResult
import org.gradle.api.Action

class LoomTestServer {

    private final ServerSocket socket = new ServerSocket(0)

    void open(Action<Controller> action) {
        Thread.start {
            try (var controller = new Controller(socket.accept())) {
                action.execute(controller)
            } catch (ignored) {
                ignored.printStackTrace()
            }
        }
    }

    void close() {
        socket.close()
    }

    String getAddress() {
        return "localhost"
    }

    int getPort() {
        return socket.localPort
    }

    static class Controller implements AutoCloseable {

        final Socket socket
        final ObjectInputStream inputStream
        final ObjectOutputStream outputStream

        Controller(Socket socket) {
            this.socket = socket
            this.outputStream = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()))
            this.outputStream.flush()
            this.inputStream = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()))
        }

        LoomTestResult testClasses(List<String> classes) {
            outputStream.writeUTF("TEST_CLASS")
            outputStream.writeUTF(String.join(';', classes))
            outputStream.flush()
            return poll()
        }

        LoomTestResult testPackage(List<String> packages) {
            outputStream.writeUTF("TEST_PACKAGE")
            outputStream.writeUTF(String.join(';', packages))
            outputStream.flush()
            return poll()
        }

        void exit(int code) {
            outputStream.writeUTF("EXIT")
            outputStream.writeInt(code)
            outputStream.flush()
        }

        @Override
        void close() {
            socket.close()
        }

        private LoomTestResult poll() {
            var result = new LoomTestResult()
            var testing = true
            while (testing) {
                var command = inputStream.readUTF()
                switch (command) {
                    case "TEST_BEGIN": {
                        testing = true
                        break
                    }
                    case "TEST_LOG": {
                        var message = inputStream.readUTF()
                        println(message)
                        break
                    }
                    case "TEST_END": {
                        result = new LoomTestResult(inputStream)
                        testing = false
                        break
                    }
                }
            }
            return result;
        }
    }
}
