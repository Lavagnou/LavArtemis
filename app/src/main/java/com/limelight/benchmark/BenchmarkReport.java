package com.limelight.benchmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.limelight.benchmark.BenchmarkTypes.Codec;
import com.limelight.benchmark.BenchmarkTypes.Goal;
import com.limelight.benchmark.BenchmarkTypes.Metrics;
import com.limelight.benchmark.BenchmarkTypes.Mode;
import com.limelight.benchmark.BenchmarkTypes.Role;
import com.limelight.benchmark.BenchmarkTypes.RunConfig;
import com.limelight.benchmark.BenchmarkTypes.RunResult;
import com.limelight.benchmark.BenchmarkTypes.Status;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A benchmark's outcome, in the JSON both clients read and write (docs/benchmark.md,
 * "Report"): a report made on the desktop opens here and the other way round.
 */
public final class BenchmarkReport {
    public static final String SCHEMA = "lavartemis-benchmark";
    public static final int VERSION = 1;
    public static final double UNSTABLE_DRIFT = 5.0;

    public String id = "";
    public long createdAtMs;
    public Mode mode = Mode.QUICK;
    public Goal goal = Goal.BALANCED;
    public String hostUuid = "";
    public String hostName = "";
    public String platform = "android";
    public String device = "";
    public String appVersion = "";
    public int width;
    public int height;
    public double fps;
    public int warmupSeconds;
    public int measureSeconds;
    public boolean cancelled;
    public double baselineDrift = -1;    // -1 until the baseline has run twice
    public List<RunResult> runs = new ArrayList<>();

    public boolean unstable() {
        return baselineDrift > UNSTABLE_DRIFT;
    }

    /** How far the baseline's balanced score moved between its first and last run, or -1. */
    public static double computeBaselineDrift(List<RunResult> runs) {
        RunResult first = null;
        RunResult last = null;
        for (RunResult r : runs) {
            if (r.status != Status.OK) {
                continue;
            }
            if (r.role == Role.BASELINE && first == null) {
                first = r;
            }
            else if (r.role == Role.BASELINE_REPEAT) {
                last = r;
            }
        }
        if (first == null || last == null) {
            return -1;
        }
        return BenchmarkScoring.roundScore(Math.abs(first.scores.balanced - last.scores.balanced));
    }

    private static SimpleDateFormat isoFormat() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format;
    }

    static JsonObject configToJson(RunConfig c) {
        JsonObject o = new JsonObject();
        o.addProperty("codec", c.codec.id);
        o.addProperty("bitrateKbps", c.bitrateKbps);
        o.addProperty("pacing", c.pacing);
        return o;
    }

    static RunConfig configFromJson(JsonObject o) {
        if (o == null) {
            return null;
        }
        Codec codec = Codec.fromId(string(o, "codec"));
        if (codec == null) {
            return null;
        }
        return new RunConfig(codec, (int) number(o, "bitrateKbps"), string(o, "pacing"));
    }

    private static JsonObject metricsToJson(Metrics m) {
        JsonObject o = new JsonObject();
        o.addProperty("hostMs", m.hostMs);
        o.addProperty("networkMs", m.networkMs);
        o.addProperty("receiveMs", m.receiveMs);
        o.addProperty("decodeMs", m.decodeMs);
        o.addProperty("queueMs", m.queueMs);
        o.addProperty("renderMs", m.renderMs);
        o.addProperty("totalMs", m.totalMs);
        o.addProperty("rttMs", m.rttMs);
        o.addProperty("rttVarianceMs", m.rttVarianceMs);
        o.addProperty("targetFps", m.targetFps);
        o.addProperty("measuredSeconds", m.measuredSeconds);
        o.addProperty("framesRendered", m.framesRendered);
        o.addProperty("framesExpected", m.framesExpected);
        o.addProperty("networkLossPct", m.networkLossPct);
        o.addProperty("presentP50Ms", m.presentP50Ms);
        o.addProperty("presentP95Ms", m.presentP95Ms);
        o.addProperty("presentP99Ms", m.presentP99Ms);
        o.addProperty("stutterPct", m.stutterPct);
        return o;
    }

    private static Metrics metricsFromJson(JsonObject o) {
        Metrics m = new Metrics();
        if (o == null) {
            return m;
        }
        m.hostMs = number(o, "hostMs");
        m.networkMs = number(o, "networkMs");
        m.receiveMs = number(o, "receiveMs");
        m.decodeMs = number(o, "decodeMs");
        m.queueMs = number(o, "queueMs");
        m.renderMs = number(o, "renderMs");
        m.totalMs = number(o, "totalMs");
        m.rttMs = number(o, "rttMs");
        m.rttVarianceMs = number(o, "rttVarianceMs");
        m.targetFps = number(o, "targetFps");
        m.measuredSeconds = number(o, "measuredSeconds");
        m.framesRendered = (long) number(o, "framesRendered");
        m.framesExpected = (long) number(o, "framesExpected");
        m.networkLossPct = number(o, "networkLossPct");
        m.presentP50Ms = number(o, "presentP50Ms");
        m.presentP95Ms = number(o, "presentP95Ms");
        m.presentP99Ms = number(o, "presentP99Ms");
        m.stutterPct = number(o, "stutterPct");
        return m;
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static double number(JsonObject o, String key) {
        JsonElement e = o.get(key);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsDouble() : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static JsonObject object(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    static JsonObject runToJson(RunResult r) {
        JsonObject run = new JsonObject();
        run.addProperty("role", r.role.id);
        run.addProperty("status", r.status.id);
        run.addProperty("note", r.note);
        run.add("requested", configToJson(r.requested));
        run.add("effective", configToJson(r.effective));
        run.add("metrics", metricsToJson(r.metrics));
        JsonObject scores = new JsonObject();
        scores.addProperty("latency", r.scores.latency);
        scores.addProperty("smoothness", r.scores.smoothness);
        scores.addProperty("balanced", r.scores.balanced);
        run.add("scores", scores);
        JsonObject spread = new JsonObject();
        spread.addProperty("latency", r.spreadLatency);
        spread.addProperty("smoothness", r.spreadSmoothness);
        run.add("spread", spread);
        return run;
    }

    /** Null for a run this version cannot understand (a codec added later...). */
    static RunResult runFromJson(JsonObject j) {
        Role role = Role.fromId(string(j, "role"));
        Status status = Status.fromId(string(j, "status"));
        RunConfig requested = configFromJson(object(j, "requested"));
        if (role == null || status == null || requested == null) {
            return null;
        }
        RunConfig effective = configFromJson(object(j, "effective"));
        RunResult r = new RunResult();
        r.role = role;
        r.status = status;
        r.requested = requested;
        r.effective = effective != null ? effective : requested.copy();
        r.note = string(j, "note");
        r.metrics = metricsFromJson(object(j, "metrics"));
        JsonObject scores = object(j, "scores");
        r.scores.latency = number(scores, "latency");
        r.scores.smoothness = number(scores, "smoothness");
        r.scores.balanced = number(scores, "balanced");
        JsonObject spread = object(j, "spread");
        r.spreadLatency = number(spread, "latency");
        r.spreadSmoothness = number(spread, "smoothness");
        return r;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("schema", SCHEMA);
        o.addProperty("version", VERSION);
        o.addProperty("id", id);
        o.addProperty("createdAt", isoFormat().format(new Date(createdAtMs)));
        o.addProperty("mode", mode.id);
        o.addProperty("goal", goal.id);
        JsonObject host = new JsonObject();
        host.addProperty("uuid", hostUuid);
        host.addProperty("name", hostName);
        o.add("host", host);
        JsonObject client = new JsonObject();
        client.addProperty("platform", platform);
        client.addProperty("device", device);
        client.addProperty("appVersion", appVersion);
        o.add("client", client);
        JsonObject stream = new JsonObject();
        stream.addProperty("width", width);
        stream.addProperty("height", height);
        stream.addProperty("fps", fps);
        o.add("stream", stream);
        o.addProperty("warmupSeconds", warmupSeconds);
        o.addProperty("measureSeconds", measureSeconds);
        o.addProperty("cancelled", cancelled);
        o.addProperty("baselineDrift", baselineDrift);

        JsonArray array = new JsonArray();
        for (RunResult r : runs) {
            array.add(runToJson(r));
        }
        o.add("runs", array);
        return o;
    }

    /** Null when the JSON is not a benchmark report this version understands. */
    public static BenchmarkReport fromJson(JsonObject o) {
        if (o == null || !SCHEMA.equals(string(o, "schema")) || number(o, "version") < 1) {
            return null;
        }
        BenchmarkReport report = new BenchmarkReport();
        report.id = string(o, "id");
        try {
            Date date = isoFormat().parse(string(o, "createdAt"));
            report.createdAtMs = date != null ? date.getTime() : 0;
        } catch (java.text.ParseException e) {
            return null;
        }
        if (report.id.isEmpty()) {
            return null;
        }
        Mode mode = Mode.fromId(string(o, "mode"));
        Goal goal = Goal.fromId(string(o, "goal"));
        report.mode = mode != null ? mode : Mode.QUICK;
        report.goal = goal != null ? goal : Goal.BALANCED;
        JsonObject host = object(o, "host");
        report.hostUuid = string(host, "uuid");
        report.hostName = string(host, "name");
        JsonObject client = object(o, "client");
        report.platform = string(client, "platform");
        report.device = string(client, "device");
        report.appVersion = string(client, "appVersion");
        JsonObject stream = object(o, "stream");
        report.width = (int) number(stream, "width");
        report.height = (int) number(stream, "height");
        report.fps = number(stream, "fps");
        report.warmupSeconds = (int) number(o, "warmupSeconds");
        report.measureSeconds = (int) number(o, "measureSeconds");
        JsonElement cancelled = o.get("cancelled");
        report.cancelled = cancelled != null && cancelled.isJsonPrimitive() && cancelled.getAsBoolean();
        report.baselineDrift = o.has("baselineDrift") ? number(o, "baselineDrift") : -1;

        JsonElement runs = o.get("runs");
        if (runs != null && runs.isJsonArray()) {
            for (JsonElement element : runs.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                RunResult r = runFromJson(element.getAsJsonObject());
                if (r == null) {
                    continue;
                }
                report.runs.add(r);
            }
        }
        return report;
    }

    public static BenchmarkReport read(Reader reader) {
        try {
            JsonElement element = JsonParser.parseReader(reader);
            return element.isJsonObject() ? fromJson(element.getAsJsonObject()) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** One run, as Game writes it at the end of a benchmark run; null if unreadable. */
    public static RunResult readRun(Reader reader) {
        try {
            JsonElement element = JsonParser.parseReader(reader);
            return element.isJsonObject() ? runFromJson(element.getAsJsonObject()) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void write(Writer writer) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        writer.write(gson.toJson(toJson()));
        writer.flush();
    }

    /** Saved reports, newest first, in one directory (files/benchmarks in the app). */
    public static final class History {
        public static final int MAX_REPORTS = 50;

        private final File directory;

        public History(File directory) {
            this.directory = directory;
        }

        private File fileFor(BenchmarkReport report) {
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return new File(directory, format.format(new Date(report.createdAtMs)) + "-" + report.id + ".json");
        }

        private File[] files() {
            File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
            if (files == null) {
                return new File[0];
            }
            // Names start with the UTC date: reverse order is newest first
            Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName()));
            return files;
        }

        public boolean save(BenchmarkReport report) {
            if (!directory.isDirectory() && !directory.mkdirs()) {
                return false;
            }
            File target = fileFor(report);
            File temp = new File(directory, target.getName() + ".tmp");
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8)) {
                report.write(writer);
            } catch (IOException e) {
                temp.delete();
                return false;
            }
            if (!temp.renameTo(target)) {
                target.delete();
                if (!temp.renameTo(target)) {
                    temp.delete();
                    return false;
                }
            }
            prune();
            return true;
        }

        public List<BenchmarkReport> load(String hostUuid) {
            List<BenchmarkReport> reports = new ArrayList<>();
            for (File file : files()) {
                try (InputStream in = new FileInputStream(file);
                     Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    BenchmarkReport report = read(reader);
                    if (report != null && (hostUuid == null || hostUuid.isEmpty() || hostUuid.equalsIgnoreCase(report.hostUuid))) {
                        reports.add(report);
                    }
                } catch (IOException ignored) {
                    // An unreadable file is left alone; the others still load
                }
            }
            // Collections.sort, not List.sort: the latter is API 24 and minSdk is 21
            Collections.sort(reports, (a, b) -> Long.compare(b.createdAtMs, a.createdAtMs));
            return reports;
        }

        public boolean remove(String id) {
            boolean removed = false;
            for (File file : files()) {
                if (file.getName().endsWith("-" + id + ".json")) {
                    removed |= file.delete();
                }
            }
            return removed;
        }

        private void prune() {
            File[] files = files();
            for (int i = MAX_REPORTS; i < files.length; i++) {
                files[i].delete();
            }
        }
    }
}
