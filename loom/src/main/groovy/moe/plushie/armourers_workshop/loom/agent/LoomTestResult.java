package moe.plushie.armourers_workshop.loom.agent;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class LoomTestResult {

    public long containersFound;
    public long containersStarted;
    public long containersSkipped;
    public long containersAborted;
    public long containersSucceeded;
    public long containersFailed;
    public long testsFound;
    public long testsStarted;
    public long testsSkipped;
    public long testsAborted;
    public long testsSucceeded;
    public long testsFailed;
    public long timeStarted;
    public long timeStartedNanos;
    public long timeFinished;
    public long timeFinishedNanos;
    public List<Failure> failures;

    public LoomTestResult() {
        failures = new ArrayList<>();
    }

    public LoomTestResult(ObjectInputStream in) throws IOException, ClassNotFoundException {
        containersFound = in.readLong();
        containersStarted = in.readLong();
        containersSkipped = in.readLong();
        containersAborted = in.readLong();
        containersSucceeded = in.readLong();
        containersFailed = in.readLong();
        testsFound = in.readLong();
        testsStarted = in.readLong();
        testsSkipped = in.readLong();
        testsAborted = in.readLong();
        testsSucceeded = in.readLong();
        testsFailed = in.readLong();
        timeStarted = in.readLong();
        timeStartedNanos = in.readLong();
        timeFinished = in.readLong();
        timeFinishedNanos = in.readLong();
        failures = new ArrayList<Failure>();
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            failures.add(new Failure(in));
        }
    }

    public void writeObject(ObjectOutputStream out) throws IOException {
        out.writeLong(containersFound);
        out.writeLong(containersStarted);
        out.writeLong(containersSkipped);
        out.writeLong(containersAborted);
        out.writeLong(containersSucceeded);
        out.writeLong(containersFailed);
        out.writeLong(testsFound);
        out.writeLong(testsStarted);
        out.writeLong(testsSkipped);
        out.writeLong(testsAborted);
        out.writeLong(testsSucceeded);
        out.writeLong(testsFailed);
        out.writeLong(timeStarted);
        out.writeLong(timeStartedNanos);
        out.writeLong(timeFinished);
        out.writeLong(timeFinishedNanos);
        out.writeInt(failures.size());
        for (Failure failure : failures) {
            failure.writeObject(out);
        }
    }

    public long getTimeStarted() {
        return timeStarted;
    }

    public long getTimeFinished() {
        return timeFinished;
    }

    public long getTotalFailureCount() {
        return getTestsFailedCount() + getContainersFailedCount();
    }

    public long getContainersFoundCount() {
        return containersFound;
    }

    public long getContainersStartedCount() {
        return containersStarted;
    }

    public long getContainersSkippedCount() {
        return containersSkipped;
    }

    public long getContainersAbortedCount() {
        return containersAborted;
    }

    public long getContainersSucceededCount() {
        return containersSucceeded;
    }

    public long getContainersFailedCount() {
        return containersFailed;
    }

    public long getTestsFoundCount() {
        return testsFound;
    }

    public long getTestsStartedCount() {
        return testsStarted;
    }

    public long getTestsSkippedCount() {
        return testsSkipped;
    }

    public long getTestsAbortedCount() {
        return testsAborted;
    }

    public long getTestsSucceededCount() {
        return testsSucceeded;
    }

    public long getTestsFailedCount() {
        return testsFailed;
    }

    public List<Failure> getFailures() {
        return failures;
    }

    @Override
    public String toString() {
        return String.format("%nTest run finished after %d ms%n[%10d containers found      ]%n[%10d containers skipped    ]%n[%10d containers started    ]%n[%10d containers aborted    ]%n[%10d containers successful ]%n[%10d containers failed     ]%n[%10d tests found           ]%n[%10d tests skipped         ]%n[%10d tests started         ]%n[%10d tests aborted         ]%n[%10d tests successful      ]%n[%10d tests failed          ]%n%n", Duration.ofNanos(this.timeFinishedNanos - this.timeStartedNanos).toMillis(), this.getContainersFoundCount(), this.getContainersSkippedCount(), this.getContainersStartedCount(), this.getContainersAbortedCount(), this.getContainersSucceededCount(), this.getContainersFailedCount(), this.getTestsFoundCount(), this.getTestsSkippedCount(), this.getTestsStartedCount(), this.getTestsAbortedCount(), this.getTestsSucceededCount(), this.getTestsFailedCount());
    }


    public static class Failure {

        private String testIdentifier;
        private Throwable exception;

        public Failure(String testIdentifier, Throwable exception) {
            this.testIdentifier = testIdentifier;
            this.exception = exception;
        }

        public Failure(ObjectInputStream in) throws IOException, ClassNotFoundException {
            testIdentifier = in.readUTF();
//            exception = (Throwable) in.readObject();
        }

        public void writeObject(ObjectOutputStream out) throws IOException {
            out.writeUTF(testIdentifier);
//            out.writeObject(exception);
        }

        public String getTestIdentifier() {
            return testIdentifier;
        }

        public Throwable getException() {
            return exception;
        }
    }
}
