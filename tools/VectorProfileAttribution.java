import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import jdk.jfr.consumer.*;

/** Read-only, benchmark-specific stack attribution. Run with JDK 17 source-file launch. */
public class VectorProfileAttribution {
    record Frame(String owner, String method, int line) {
        String site() { return owner + "." + method + ":" + line; }
    }
    record Attribution(String legacy, String group, String site, String caller) {}
    static final String DIAGNOSTIC = "com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics";
    static final String SAMPLER = "com.cra.figaro.algorithm.sampling.VectorSliceSampler";
    static final String BENCHMARK = "com.cra.figaro.example.VectorSamplingPerformance";
    static String clean(String name) {
        return name.replace('/', '.').replaceAll("\\+0x[0-9a-fA-F]+(?:\\.\\d+)?", "");
    }
    static Attribution classify(List<Frame> frames) {
        int diagnostic = -1, sampler = -1, callback = -1;
        for (int i = 0; i < frames.size(); i++) {
            Frame f = frames.get(i);
            if (diagnostic < 0 && f.owner.startsWith(DIAGNOSTIC)) diagnostic = i;
            if (sampler < 0 && f.owner.startsWith(SAMPLER)) sampler = i;
            if (callback < 0 && f.owner.startsWith(BENCHMARK) && f.method.startsWith("density")) callback = i;
        }
        if (diagnostic >= 0) {
            Frame f = frames.get(diagnostic);
            String caller = "none";
            if (f.method.equals("interrupted")) {
                caller = "unresolved";
                for (int i = diagnostic + 1; i < frames.size(); i++) {
                    Frame next = frames.get(i);
                    if (next.owner.startsWith(DIAGNOSTIC) && !next.method.equals("interrupted")) {
                        caller = next.site(); break;
                    }
                }
            }
            return new Attribution("diagnostics", "diagnostics", f.site(), caller);
        }
        if (sampler >= 0) {
            Frame f = frames.get(sampler);
            if (callback >= 0 && callback < sampler)
                return new Attribution("sampling", "callbackObserved", f.site(), frames.get(callback).site());
            // Pinned to the unchanged benchmarked source: evaluate's callback invocation is line 86.
            boolean boundary = f.method.equals("evaluate$1") && f.line == 86;
            return new Attribution("sampling", boundary ? "callbackBoundaryUnresolved" : "samplerObserved", f.site(), "none");
        }
        if (frames.isEmpty()) return new Attribution("unknown", "unknown", "unknown", "none");
        return new Attribution("other", callback >= 0 ? "callbackUnanchored" : "other", frames.get(0).site(),
                               callback >= 0 ? frames.get(callback).site() : "none");
    }
    static void check(boolean condition) { if (!condition) throw new AssertionError("Attribution contract failed"); }
    static void selfTest() {
        Frame allocation = new Frame("java.lang.Double", "valueOf", 1);
        Frame callback = new Frame(BENCHMARK + "$", "density$$anonfun$1", 19);
        Frame boundary = new Frame(SAMPLER + "$", "evaluate$1", 86);
        Frame guard = new Frame(SAMPLER + "$", "evaluate$1", 84);
        Frame stop = new Frame(DIAGNOSTIC + "$", "interrupted", 10);
        Frame rank = new Frame(DIAGNOSTIC + "$", "rankNormalize", 184);
        check(classify(List.of(allocation, callback, boundary)).group.equals("callbackObserved"));
        check(classify(List.of(allocation, boundary)).group.equals("callbackBoundaryUnresolved"));
        check(classify(List.of(allocation, guard)).group.equals("samplerObserved"));
        check(classify(List.of(allocation, callback)).group.equals("callbackUnanchored"));
        check(classify(List.of()).group.equals("unknown"));
        check(classify(List.of(allocation)).group.equals("other"));
        check(classify(List.of(stop, rank)).caller.equals(rank.site()));
        check(classify(List.of(stop)).caller.equals("unresolved"));
        check(classify(List.of(guard, callback)).group.equals("samplerObserved"));
        check(classify(List.of(stop, rank, boundary)).legacy.equals("diagnostics"));
        System.out.println("10 attribution classification checks passed");
    }
    static String quote(String text) { return "\"" + text.replace("\"", "\"\"") + "\""; }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-test")) { selfTest(); return; }
        if (args.length != 1) throw new IllegalArgumentException("Expected an existing sorting-checkpoint JFR path or --self-test");
        Path path = Path.of(args[0]);
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = input.read(buffer)) >= 0) hash.update(buffer, 0, n);
        }
        String sha = HexFormat.of().formatHex(hash.digest());
        Map<String, long[]> totals = new TreeMap<>();
        long lost = 0;
        try (RecordingFile recording = new RecordingFile(path)) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                String type = event.getEventType().getName();
                if (type.equals("jdk.DataLoss")) lost = Math.addExact(lost, event.getLong("amount"));
                if (!type.equals("jdk.ObjectAllocationSample") && !type.equals("jdk.ExecutionSample")) continue;
                RecordedStackTrace stack = event.getStackTrace();
                List<Frame> frames = new ArrayList<>();
                if (stack != null) for (RecordedFrame frame : stack.getFrames()) {
                    RecordedMethod m = frame.getMethod();
                    frames.add(new Frame(clean(m.getType().getName()), m.getName(), frame.getLineNumber()));
                }
                Attribution a = classify(frames);
                boolean allocation = type.equals("jdk.ObjectAllocationSample");
                String detail = allocation ? clean(event.getClass("objectClass").getName()) :
                    frames.isEmpty() ? "unknown" : frames.get(0).owner + "." + frames.get(0).method;
                String key = String.join("\t", allocation ? "allocation" : "execution", a.legacy, a.group,
                    detail, a.site, a.caller, Boolean.toString(stack != null && stack.isTruncated()));
                long[] value = totals.computeIfAbsent(key, ignored -> new long[2]);
                value[0] = Math.addExact(value[0], 1);
                value[1] = Math.addExact(value[1], allocation ? event.getLong("weight") : 1);
            }
        }
        if (lost != 0) throw new IllegalStateException("Recording reported data loss");
        System.out.println("recordingSha256,kind,legacyGroup,group,detail,site,caller,truncated,count,value");
        for (var entry : totals.entrySet()) {
            String[] fields = entry.getKey().split("\t");
            List<String> cells = new ArrayList<>(); cells.add(sha); Collections.addAll(cells, fields);
            cells.add(Long.toString(entry.getValue()[0])); cells.add(Long.toString(entry.getValue()[1]));
            System.out.println(String.join(",", cells.stream().map(VectorProfileAttribution::quote).toList()));
        }
    }
}
