package moe.plushie.armourers_workshop.loom.core;

import moe.plushie.armourers_workshop.loom.core.agent.LoomTestResult;
import org.gradle.api.logging.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;

public class LoomTestServer implements AutoCloseable {

    private final ServerSocket socket;

    public LoomTestServer(int port) throws IOException {
        this.socket = new ServerSocket(port);
    }

    public void accept(ThrowableAction<Controller> action, int timeout, Runnable timeoutHandler) throws IOException {
        socket.setSoTimeout(timeout);
        var thread = new Thread(() -> {
            try (var controller = new Controller(socket.accept())) {
                action.execute(controller);
            } catch (SocketTimeoutException e) {
                if (timeoutHandler != null) {
                    timeoutHandler.run();
                }
            } catch (Throwable ignored) {
                // ig
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    public String getAddress() {
        return "localhost";
    }

    public int getPort() {
        return socket.getLocalPort();
    }

    public static class Controller implements AutoCloseable {

        private final Socket socket;
        private final ObjectInputStream inputStream;
        private final ObjectOutputStream outputStream;

        private Logger logger;

        public Controller(Socket socket) throws IOException {
            this.socket = socket;
            this.outputStream = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            this.outputStream.flush();
            this.inputStream = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
        }

        public LoomTestResult run(List<String> classes, List<String> packages) throws IOException {
            outputStream.writeUTF("RUN");
            outputStream.writeUTF(String.join(";", classes));
            outputStream.writeUTF(String.join(";", packages));
            outputStream.flush();
            return poll();
        }

        public void exit(int code) throws IOException {
            outputStream.writeUTF("EXIT");
            outputStream.writeInt(code);
            outputStream.flush();
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }

        public void setLogger(Logger logger) {
            this.logger = logger;
        }

        public Logger getLogger() {
            return logger;
        }

        private LoomTestResult poll() throws IOException {
            while (true) {
                var command = inputStream.readUTF();
                switch (command) {
                    case "TEST_BEGIN" -> {
                        // nope
                    }
                    case "TEST_LOG" -> {
                        var message = inputStream.readUTF();
                        if (logger != null) {
                            logger.info("{}", message);
                        }
                    }
                    case "TEST_END" -> {
                        return new LoomTestResult(inputStream);
                    }
                }
            }
        }
    }
}
