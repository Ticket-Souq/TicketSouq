package org.ticketsouq.sharedmodule.observability;

import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts a JFR recording produced by {@link jdk.jfr.Recording} into pprof
 * profiles (CPU + allocation) and pushes them to a Pyroscope server.
 */
final class JfrProfileExporter {

    private static final String EXEC_SAMPLE = "jdk.ExecutionSample";
    private static final String NATIVE_SAMPLE = "jdk.NativeMethodSample";
    private static final String ALLOC_SAMPLE = "jdk.ObjectAllocationSample";
    private static final String ALLOC_TLAB = "jdk.ObjectAllocationInNewTLAB";
    private static final String ALLOC_OUTSIDE = "jdk.ObjectAllocationOutsideTLAB";
    private static final long ALLOC_SAMPLING_BYTES = 512L * 1024L;
    private static final String EXPORTER_THREAD = "pyroscope-pprof-exporter";

    private JfrProfileExporter() {
    }

    static final class Profiles {
        byte[] cpu;
        byte[] alloc;
    }

    static Profiles convert(Path jfrFile, long windowStartNanos, long windowEndNanos, long sampleMs)
            throws IOException {
        long cpuIntervalNs = sampleMs * 1_000_000L;
        PprofBuilder cpu = new PprofBuilder("cpu", "nanoseconds", "cpu", "nanoseconds", cpuIntervalNs);
        PprofBuilder alloc = new PprofBuilder("alloc_space", "bytes", "space", "bytes", ALLOC_SAMPLING_BYTES);
        cpu.setTimeWindow(windowStartNanos, windowEndNanos - windowStartNanos);
        alloc.setTimeWindow(windowStartNanos, windowEndNanos - windowStartNanos);

        try (RecordingFile rf = new RecordingFile(jfrFile)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent ev = rf.readEvent();
                if (EXPORTER_THREAD.equals(ev.getThread() != null ? ev.getThread().getJavaName() : null)) {
                    continue;
                }
                String name = ev.getEventType().getName();
                if (EXEC_SAMPLE.equals(name) || NATIVE_SAMPLE.equals(name)) {
                    List<PprofBuilder.Frame> stack = extractStack(ev);
                    if (!stack.isEmpty()) {
                        cpu.addSample(stack, cpuIntervalNs);
                    }
                } else if (ALLOC_SAMPLE.equals(name) || ALLOC_TLAB.equals(name) || ALLOC_OUTSIDE.equals(name)) {
                    List<PprofBuilder.Frame> stack = extractStack(ev);
                    if (!stack.isEmpty()) {
                        long bytes = allocationSize(ev);
                        if (bytes > 0) {
                            alloc.addSample(stack, bytes);
                        }
                    }
                }
            }
        }

        Profiles profiles = new Profiles();
        if (!cpu.isEmpty()) {
            profiles.cpu = cpu.buildGzip();
        }
        if (!alloc.isEmpty()) {
            profiles.alloc = alloc.buildGzip();
        }
        return profiles;
    }

    private static long allocationSize(RecordedEvent ev) {
        if (ev.hasField("weight")) {
            return ev.getLong("weight");
        }
        if (ev.hasField("allocationSize")) {
            return ev.getLong("allocationSize");
        }
        return 0;
    }

    /**
     * JFR stack frames are already ordered leaf-first (top of stack first),
     * which is the order pprof/pyroscope expect (locations[0] = innermost).
     */
    private static List<PprofBuilder.Frame> extractStack(RecordedEvent ev) {
        RecordedStackTrace st = ev.getStackTrace();
        if (st == null) {
            return List.of();
        }
        List<RecordedFrame> frames = st.getFrames();
        List<PprofBuilder.Frame> stack = new ArrayList<>(frames.size());
        boolean hasJava = false;
        for (RecordedFrame f : frames) {
            RecordedMethod m = f.getMethod();
            if (m == null || m.getType() == null) {
                continue;
            }
            String func = m.getType().getName() + "." + m.getName();
            stack.add(new PprofBuilder.Frame(func, f.getLineNumber()));
            hasJava = true;
        }
        if (!hasJava) {
            return List.of();
        }
        return stack;
    }

    static int upload(String server, String app, long fromSeconds, long untilSeconds, byte[] gzippedPprof)
            throws IOException, InterruptedException {
        String url = server + "/ingest?name=" + URLEncoder.encode(app, StandardCharsets.UTF_8)
                + "&units=samples&aggregationType=sum"
                + "&from=" + fromSeconds
                + "&until=" + untilSeconds
                + "&spyName=javaspy&sampleRate=100&format=pprof";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "binary/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(gzippedPprof))
                .timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode();
    }
}
