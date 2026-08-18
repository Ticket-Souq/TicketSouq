package org.ticketsouq.sharedmodule.observability;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jdk.jfr.Recording;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Continuous profiling exporter.
 *
 * <p>Records the JVM with {@link jdk.jfr.Recording} (works on any JDK and OS,
 * including JDK 25 on Windows) and pushes the samples to a Pyroscope server as
 * pprof profiles via {@code /ingest?format=pprof}. Pyroscope v2 only parses
 * async-profiler generated JFR, so the raw JFR produced by the JDK is silently
 * dropped; converting to pprof in-process avoids that entirely.
 */
@Configuration
public class PyroscopeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PyroscopeConfiguration.class);

    @Value("${pyroscope.agent-enabled:false}")
    private boolean agentEnabled;

    @Value("${pyroscope.application-name:}")
    private String applicationName;

    @Value("${spring.application.name:}")
    private String springApplicationName;

    @Value("${pyroscope.server-address:http://localhost:4040}")
    private String serverAddress;

    @Value("${pyroscope.interval-ms:10000}")
    private long intervalMs;

    @Value("${pyroscope.sample-ms:10}")
    private long sampleMs;

    private final AtomicBoolean running = new AtomicBoolean();
    private Thread worker;
    private volatile Recording current;

    @PostConstruct
    public void startProfiling() {
        if (!agentEnabled || running.get()) {
            return;
        }
        running.set(true);
        worker = new Thread(this::run, "pyroscope-pprof-exporter");
        worker.setDaemon(true);
        worker.start();
        log.info("Pyroscope pprof exporter started for application '{}' -> {}", resolveApplicationName(), serverAddress);
    }

    @PreDestroy
    public void stopProfiling() {
        running.set(false);
        if (worker != null) {
            worker.interrupt();
        }
    }

    private void run() {
        String app = resolveApplicationName();
        try {
            Recording rec = newRecording();
            rec.start();
            current = rec;
            long windowStart = System.currentTimeMillis();

            while (running.get()) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (!running.get()) {
                    break;
                }
                long windowEnd = System.currentTimeMillis();

                Recording next = newRecording();
                next.start();

                Path tmp = Files.createTempFile("pyroscope", ".jfr");
                try {
                    rec.dump(tmp);
                    long from = windowStart / 1000;
                    long until = windowEnd / 1000;
                    long startNs = windowStart * 1_000_000L;
                    long endNs = windowEnd * 1_000_000L;

                    JfrProfileExporter.Profiles profiles =
                            JfrProfileExporter.convert(tmp, startNs, endNs, sampleMs);
                    if (profiles.cpu != null) {
                        upload(app, from, until, profiles.cpu, "cpu");
                    }
                    if (profiles.alloc != null) {
                        upload(app, from, until, profiles.alloc, "alloc");
                    }
                } catch (Exception e) {
                    log.warn("Pyroscope profile export failed: {}", e.toString());
                } finally {
                    Files.deleteIfExists(tmp);
                    rec.close();
                }

                windowStart = windowEnd;
                rec = next;
                current = rec;
            }

            rec.close();
        } catch (Exception e) {
            log.error("Pyroscope pprof exporter stopped unexpectedly", e);
        } finally {
            current = null;
            running.set(false);
        }
    }

    private void upload(String app, long from, long until, byte[] pprof, String kind) {
        try {
            int code = JfrProfileExporter.upload(serverAddress, app, from, until, pprof);
            if (code >= 400) {
                log.warn("Pyroscope {} upload -> HTTP {} at {}", kind, code, LocalTime.now());
            }
        } catch (Exception e) {
            log.warn("Pyroscope {} upload failed: {}", kind, e.toString());
        }
    }

    private Recording newRecording() {
        Recording r = new Recording();
        r.setName("pyroscope");
        r.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(sampleMs));
        r.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(sampleMs));
        r.enable("jdk.ObjectAllocationSample").withPeriod(Duration.ofNanos(512L * 1024L));
        r.setMaxSize(128L * 1024 * 1024);
        return r;
    }

    private String resolveApplicationName() {
        if (applicationName != null && !applicationName.isBlank()) {
            return applicationName;
        }
        if (springApplicationName != null && !springApplicationName.isBlank()) {
            return springApplicationName;
        }
        return "java-app";
    }
}
