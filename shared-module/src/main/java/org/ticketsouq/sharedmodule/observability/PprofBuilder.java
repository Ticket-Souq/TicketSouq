package org.ticketsouq.sharedmodule.observability;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

/**
 * Minimal pprof (protobuf) profile builder, hand-rolled to avoid runtime dependencies.
 */
final class PprofBuilder {

    static final class Frame {
        final String function;
        final int line;

        Frame(String function, int line) {
            this.function = function;
            this.line = line;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Frame)) {
                return false;
            }
            Frame f = (Frame) o;
            return line == f.line && function.equals(f.function);
        }

        @Override
        public int hashCode() {
            return Objects.hash(function, line);
        }
    }

    private final String sampleType;
    private final String sampleUnit;
    private final String periodType;
    private final String periodUnit;
    private final long period;

    private final List<String> strings = new ArrayList<>();
    private final Map<String, Integer> stringIdx = new HashMap<>();
    private final Map<String, Long> functionIds = new HashMap<>();
    private final Map<Frame, Long> locationIds = new HashMap<>();
    private final Map<List<Long>, long[]> samples = new LinkedHashMap<>();
    private long nextId = 1;
    private long timeNanos;
    private long durationNanos;

    PprofBuilder(String sampleType, String sampleUnit, String periodType, String periodUnit, long period) {
        this.sampleType = sampleType;
        this.sampleUnit = sampleUnit;
        this.periodType = periodType;
        this.periodUnit = periodUnit;
        this.period = period;
        strings.add("");
    }

    private int string(String s) {
        Integer i = stringIdx.get(s);
        if (i == null) {
            i = strings.size();
            strings.add(s);
            stringIdx.put(s, i);
        }
        return i;
    }

    /** @param frames stack ordered leaf-first (innermost call first, root last). */
    void addSample(List<Frame> frames, long value) {
        List<Long> key = new ArrayList<>(frames.size());
        for (Frame f : frames) {
            key.add(locationFor(f));
        }
        long[] v = samples.get(key);
        if (v == null) {
            samples.put(key, new long[]{value});
        } else {
            v[0] += value;
        }
    }

    private long locationFor(Frame f) {
        Long id = locationIds.get(f);
        if (id == null) {
            id = nextId++;
            locationIds.put(f, id);
        }
        return id;
    }

    private long functionFor(String name) {
        Long id = functionIds.get(name);
        if (id == null) {
            id = nextId++;
            functionIds.put(name, id);
        }
        return id;
    }

    void setTimeWindow(long timeNanos, long durationNanos) {
        this.timeNanos = timeNanos;
        this.durationNanos = durationNanos;
    }

    boolean isEmpty() {
        return samples.isEmpty();
    }

    byte[] buildGzip() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(8192);
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(build());
        }
        return bos.toByteArray();
    }

    private byte[] build() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);

        // Register period type/unit strings before the string table is dumped
        // (field 6), otherwise the period type index would point past the table.
        string(periodType);
        string(periodUnit);

        fieldMessage(out, 1, valueType(sampleType, sampleUnit));

        for (Map.Entry<List<Long>, long[]> e : samples.entrySet()) {
            ByteArrayOutputStream s = new ByteArrayOutputStream(64);
            packedVarints(s, 1, e.getKey().stream().mapToLong(Long::longValue).toArray());
            packedVarints(s, 2, e.getValue());
            fieldBytes(out, 2, s.toByteArray());
        }

        for (Map.Entry<Frame, Long> e : locationIds.entrySet()) {
            Frame f = e.getKey();
            long fnId = functionFor(f.function);
            ByteArrayOutputStream loc = new ByteArrayOutputStream(32);
            fieldVarint(loc, 1, e.getValue());
            ByteArrayOutputStream line = new ByteArrayOutputStream(16);
            fieldVarint(line, 1, fnId);
            fieldVarint(line, 2, f.line);
            fieldBytes(loc, 4, line.toByteArray());
            fieldBytes(out, 4, loc.toByteArray());
        }

        for (Map.Entry<String, Long> e : functionIds.entrySet()) {
            int nameIdx = string(e.getKey());
            ByteArrayOutputStream fn = new ByteArrayOutputStream(32);
            fieldVarint(fn, 1, e.getValue());
            fieldVarint(fn, 2, nameIdx);
            fieldVarint(fn, 3, nameIdx);
            fieldBytes(out, 5, fn.toByteArray());
        }

        for (String s : strings) {
            fieldBytes(out, 6, s.getBytes(StandardCharsets.UTF_8));
        }

        fieldVarint(out, 9, timeNanos);
        fieldVarint(out, 10, durationNanos);

        fieldMessage(out, 11, valueType(periodType, periodUnit));
        fieldVarint(out, 12, period);

        return out.toByteArray();
    }

    private byte[] valueType(String type, String unit) {
        ByteArrayOutputStream vt = new ByteArrayOutputStream(16);
        fieldVarint(vt, 1, string(type));
        fieldVarint(vt, 2, string(unit));
        return vt.toByteArray();
    }

    private static void fieldMessage(ByteArrayOutputStream out, int no, byte[] data) {
        fieldBytes(out, no, data);
    }

    private static void fieldBytes(ByteArrayOutputStream out, int no, byte[] data) {
        writeVarint(out, ((long) no << 3) | 2);
        writeVarint(out, data.length);
        out.write(data, 0, data.length);
    }

    private static void fieldVarint(ByteArrayOutputStream out, int no, long v) {
        writeVarint(out, ((long) no << 3) | 0);
        writeVarint(out, v);
    }

    private static void packedVarints(ByteArrayOutputStream out, int no, long[] vals) {
        ByteArrayOutputStream tmp = new ByteArrayOutputStream(32);
        for (long v : vals) {
            writeVarint(tmp, v);
        }
        fieldBytes(out, no, tmp.toByteArray());
    }

    private static void writeVarint(ByteArrayOutputStream out, long v) {
        while ((v & ~0x7FL) != 0) {
            out.write(((int) v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write((int) v);
    }
}
