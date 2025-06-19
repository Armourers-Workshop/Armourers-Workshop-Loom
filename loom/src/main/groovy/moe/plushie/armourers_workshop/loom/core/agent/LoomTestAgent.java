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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("unused")
public class LoomTestAgent implements Runnable {

    private String address = "localhost";
    private int port = 5210;

    public LoomTestAgent(String[] args) {
        //-Dname=value
        parseProperty(args, (key, value) -> {
            var decodedValue = URLDecoder.decode(value, StandardCharsets.UTF_8);
            System.setProperty(key, decodedValue);
        });
        //--address=localhost:5009
        parseArg(args, "address", value -> {
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
                    case "RUN": {
                        var classes = inputStream.readUTF();
                        var packages = inputStream.readUTF();
                        test(classes, packages, outputStream);
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

    private void test(String classes, String packages, ObjectOutputStream outputStream) throws IOException {
        outputStream.writeUTF("TEST_START");
        outputStream.flush();

        // set up the selector.
        var builder = LauncherDiscoveryRequestBuilder.request();
        builder.selectors(selector(classes, DiscoverySelectors::selectClass));
        builder.selectors(selector(packages, DiscoverySelectors::selectPackage));

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

    private void parseArg(String[] args, String opt, Consumer<String> consumer) {
        var key = String.format("--%s=", opt);
        for (var arg : args) {
            if (arg.startsWith(key)) {
                consumer.accept(arg.substring(key.length()));
            }
        }
    }

    private void parseProperty(String[] args, BiConsumer<String, String> consumer) {
        var key = "-D";
        for (var arg : args) {
            if (arg.startsWith(key)) {
                var value = arg.substring(key.length()).split("=");
                if (value.length == 2) {
                    consumer.accept(value[0], value[1]);
                } else {
                    consumer.accept(value[0], "");
                }
            }
        }
    }

    private List<? extends DiscoverySelector> selector(String names, Function<String, DiscoverySelector> transform) {
        var selectors = new ArrayList<DiscoverySelector>();
        for (var name : names.split(";")) {
            if (!name.isEmpty()) {
                selectors.add(transform.apply(name));
            }
        }
        return selectors;
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

        var printContents = new StringWriter();
        summary.printTo(new PrintWriter(printContents));
        result.printContents = printContents.toString();

        var printFailuresContents = new StringWriter();
        summary.printFailuresTo(new PrintWriter(printFailuresContents), 20);
        result.printFailuresContents = printFailuresContents.toString();

        return result;
    }
}
