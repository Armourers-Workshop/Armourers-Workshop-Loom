package moe.plushie.armourers_workshop.loom.core.agent;

import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.LoggingListener;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("unused")
public class LoomTestAgent implements Runnable {

    private String address = "localhost";
    private int port = 5200;

    public LoomTestAgent(String[] args) {
        //--address=localhost:5009
        parse(args, "address", value -> {
            var parts = value.split(":");
            if (parts.length == 2) {
                address = parts[0];
                port = Integer.parseInt(parts[1]);
            } else {
                port = Integer.parseInt(parts[0]);
            }
        });
    }

    @Override
    public void run() {
        // create a daemon thread.
        var thread = new Thread(this::process, "junit-worker");
        thread.setDaemon(true);
        thread.start();
    }

    private void process() {
        // create a socket to receive test command and the send result.
        try (var socket = new Socket(address, port)) {
            var outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.flush();
            var inputStream = new ObjectInputStream(socket.getInputStream());
            while (true) {
                var command = inputStream.readUTF();
                switch (command) {
                    case "TEST_CLASS": {
                        var name = inputStream.readUTF();
                        dispatch(name, DiscoverySelectors::selectClass, outputStream);
                        break;
                    }
                    case "TEST_PACKAGE": {
                        var name = inputStream.readUTF();
                        dispatch(name, DiscoverySelectors::selectPackage, outputStream);
                        break;
                    }
                    case "EXIT": {
                        System.exit(inputStream.readInt());
                        break;
                    }
                    default: {
                        throw new RuntimeException("Unknown command: " + command);
                    }
                }
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            System.exit(-1);
        }
    }

    private void dispatch(String value, Function<String, DiscoverySelector> transform, ObjectOutputStream outputStream) throws IOException {
        outputStream.writeUTF("TEST_START");
        outputStream.flush();

        // set up the selector.
        var builder = LauncherDiscoveryRequestBuilder.request();
        for (var name : value.split(";")) {
            builder.selectors(transform.apply(name));
        }

        // set up the logger and launch test.
        var request = builder.build();
        var summaryListener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, summaryListener, LoggingListener.forBiConsumer((e, message) -> {
            try {
                outputStream.writeUTF("TEST_LOG");
                outputStream.writeUTF(message.get());
                outputStream.flush();
            } catch (Exception ignored) {
            }
        }));

        var result = encode(summaryListener.getSummary());

        outputStream.writeUTF("TEST_END");
        result.writeObject(outputStream);
        outputStream.flush();
    }

    private void parse(String[] args, String opt, Consumer<String> consumer) {
        var key = String.format("--%s=", opt);
        for (var arg : args) {
            if (arg.startsWith(key)) {
                consumer.accept(arg.substring(key.length()));
            }
        }
    }

    private LoomTestResult encode(TestExecutionSummary summary) throws IOException {
        var result = new LoomTestResult();
        result.timeStarted = summary.getTimeStarted();
        result.timeFinished = summary.getTimeFinished();
        result.containersFound = summary.getContainersFoundCount();
        result.containersStarted = summary.getContainersStartedCount();
        result.containersSkipped = summary.getContainersSkippedCount();
        result.containersAborted = summary.getContainersAbortedCount();
        result.containersSucceeded = summary.getContainersSucceededCount();
        result.containersFailed = summary.getContainersFailedCount();
        result.testsFound = summary.getTestsFoundCount();
        result.testsStarted = summary.getTestsStartedCount();
        result.testsSkipped = summary.getTestsSkippedCount();
        result.testsAborted = summary.getTestsAbortedCount();
        result.testsSucceeded = summary.getTestsSucceededCount();
        result.testsFailed = summary.getTestsFailedCount();
        result.failures = new ArrayList<>();
        for (var failure : summary.getFailures()) {
            result.failures.add(new LoomTestResult.Failure(failure.getTestIdentifier().toString(), failure.getException()));
        }
        return result;
    }
}
