package rhworld.engine.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import rhworld.engine.arith.Arith;
import rhworld.engine.arith.PrimeTable;
import rhworld.engine.cert.Certificate;
import rhworld.engine.contract.Verdict;
import rhworld.engine.json.Json;
import rhworld.engine.json.JsonException;
import rhworld.engine.telemetry.Telemetry;
import rhworld.engine.world.GmWorld;
import rhworld.engine.world.PublicWorld;

public final class Cli {
    static final String USAGE = "usage: rhworld certify (--n <decimal> | --n-file <file> | --gm <gm.json>) [--p <decimal>] [--proofs <file>]"
            + " [--out <file>] [--telemetry lines|json] | verify <cert.json> [--telemetry lines|json]"
            + " | bench --moduli <file.json> [--repeat <count>] [--report <file.json>]";

    private static final class UsageError extends Exception {
        private static final long serialVersionUID = 1L;

        UsageError(String message) {
            super(message);
        }
    }

    private record Output(Telemetry t, boolean json, PrintStream err) {
        int done(Object... fields) {
            Object[] all = new Object[fields.length + 1];
            all[0] = "verdict";
            System.arraycopy(fields, 0, all, 1, fields.length);
            t.event(json ? "verdict" : "rhworld", all);
            return 0;
        }

        int refused(String reason) {
            if (json) t.event("verdict", "verdict", "refused", "reason", reason);
            else err.println("refused: " + reason);
            return 2;
        }
    }

    private Cli() {}

    public static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            if (args.length == 0) throw new UsageError("no command");
            Map<String, String> opts = options(args);
            String mode = opts.getOrDefault("--telemetry", "lines");
            if (!mode.equals("lines") && !mode.equals("json")) throw new UsageError("telemetry must be lines or json");
            Output o = new Output(mode.equals("json") ? Telemetry.jsonLines(err) : Telemetry.lines(err), mode.equals("json"), err);
            return switch (args[0]) {
                case "certify" -> certify(opts, out, o);
                case "verify" -> verify(opts, o);
                case "bench" -> bench(opts, out, err);
                default -> throw new UsageError("unknown command " + args[0]);
            };
        } catch (UsageError e) {
            err.println(USAGE);
            err.println("error: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            err.println("error: io " + e.getMessage());
            return 1;
        } catch (JsonException | ClassCastException e) {
            err.println("error: input malformed " + e.getMessage());
            return 1;
        }
    }

    private static Map<String, String> options(String[] args) throws UsageError {
        Map<String, String> opts = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                if (opts.containsKey("")) throw new UsageError("unexpected argument " + args[i]);
                opts.put("", args[i]);
                continue;
            }
            if (i + 1 >= args.length) throw new UsageError("missing value for " + args[i]);
            if (opts.put(args[i], args[++i]) != null) throw new UsageError("repeated option " + args[i - 1]);
        }
        return opts;
    }

    private static BigInteger decimal(String s, String what) throws UsageError {
        if (s == null || !s.strip().matches("[0-9]+")) throw new UsageError(what + " must be a decimal integer");
        return new BigInteger(s.strip());
    }

    @SuppressWarnings("unchecked")
    private static int certify(Map<String, String> opts, PrintStream out, Output o) throws UsageError, IOException {
        Telemetry t = o.t();
        BigInteger n;
        Optional<BigInteger> p = Optional.empty();
        if (opts.containsKey("--gm")) {
            Map<String, Object> gm = (Map<String, Object>) Json.parse(Files.readString(Path.of(opts.get("--gm"))));
            n = decimal((String) gm.get("n"), "gm n");
            if (gm.containsKey("p")) p = Optional.of(decimal((String) gm.get("p"), "gm p"));
        } else if (opts.containsKey("--n-file")) {
            n = decimal(Files.readString(Path.of(opts.get("--n-file"))), "n file");
        } else {
            n = decimal(opts.get("--n"), "--n");
        }
        if (opts.containsKey("--p")) p = Optional.of(decimal(opts.get("--p"), "--p"));
        Optional<GmWorld.Proofs> proofs = Optional.empty();
        if (opts.containsKey("--proofs")) {
            Map<String, Object> pr = (Map<String, Object>) Json.parse(Files.readString(Path.of(opts.get("--proofs"))));
            proofs = Optional.of(new GmWorld.Proofs((Map<String, Object>) pr.get("p"), (Map<String, Object>) pr.get("q")));
        }
        Verdict<PublicWorld> w = PublicWorld.open(n, t);
        if (w instanceof Verdict.Refused<PublicWorld> r) return o.refused(r.reason());
        PublicWorld world = ((Verdict.Ok<PublicWorld>) w).value();
        Certificate cert;
        if (p.isPresent()) {
            Verdict<GmWorld> g = GmWorld.reveal(world, p.get(), proofs, t);
            if (g instanceof Verdict.Refused<GmWorld> r) return o.refused(r.reason());
            cert = Certificate.of(((Verdict.Ok<GmWorld>) g).value());
        } else {
            cert = Certificate.of(world);
        }
        if (opts.containsKey("--out")) Files.writeString(Path.of(opts.get("--out")), cert.json() + "\n");
        else out.println(cert.json());
        return o.done("accepted", "world_id", cert.worldId(), "revealed", p.isPresent());
    }

    private static int verify(Map<String, String> opts, Output o) throws UsageError, IOException {
        Telemetry t = o.t();
        String file = opts.get("");
        if (file == null) throw new UsageError("verify needs a certificate file");
        Verdict<Certificate.Report> v = Certificate.verify(Files.readString(Path.of(file)), t);
        if (v instanceof Verdict.Refused<Certificate.Report> r) return o.refused(r.reason());
        Certificate.Report rep = ((Verdict.Ok<Certificate.Report>) v).value();
        return o.done("verified", "world_id", rep.worldId(), "revealed", rep.revealed(), "factors_status", rep.factorsStatus());
    }

    @SuppressWarnings("unchecked")
    private static int bench(Map<String, String> opts, PrintStream out, PrintStream err) throws UsageError, IOException {
        if (!opts.containsKey("--moduli")) throw new UsageError("bench needs --moduli");
        int repeat = decimal(opts.getOrDefault("--repeat", "3"), "--repeat").intValueExact();
        if (repeat < 1 || repeat > 1000) throw new UsageError("--repeat must be in 1..1000");
        byte[] raw = Files.readAllBytes(Path.of(opts.get("--moduli")));
        List<Object> items = (List<Object>) Json.parse(new String(raw, StandardCharsets.UTF_8));
        List<Object> rows = new ArrayList<>();
        for (Object o : items) {
            Map<String, Object> row = benchOne((Map<String, Object>) o, repeat);
            rows.add(row);
            out.println(Json.write(row));
        }
        if (opts.containsKey("--report")) {
            Path report = Path.of(opts.get("--report"));
            Map<String, Object> m = new TreeMap<>();
            m.put("schema", "rhworld.bench/1");
            m.put("engine_version", Certificate.ENGINE_VERSION);
            m.put("moduli_sha256", sha256(raw));
            m.put("repeat", repeat);
            m.put("timing", "min over repeats, microseconds, wall clock");
            m.put("machine", machine());
            m.put("rows", rows);
            if (report.getParent() != null) Files.createDirectories(report.getParent());
            Files.writeString(report, Json.write(m) + "\n");
            err.println("[bench] report=" + report + " rows=" + rows.size());
        }
        return 0;
    }

    private static Map<String, Object> machine() {
        Map<String, Object> m = new TreeMap<>();
        m.put("os_name", System.getProperty("os.name"));
        m.put("os_arch", System.getProperty("os.arch"));
        m.put("os_version", System.getProperty("os.version"));
        m.put("java_version", System.getProperty("java.version"));
        m.put("java_vendor", System.getProperty("java.vendor"));
        m.put("vm_name", System.getProperty("java.vm.name"));
        m.put("processors", Runtime.getRuntime().availableProcessors());
        m.put("max_heap_mb", Runtime.getRuntime().maxMemory() >> 20);
        m.put("cpu_model", cpuModel());
        return m;
    }

    private static String cpuModel() {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/cpuinfo"))) {
                if (line.startsWith("model name")) return line.substring(line.indexOf(':') + 1).strip();
            }
        } catch (IOException e) {
            return "unknown";
        }
        return "unknown";
    }

    private static String sha256(byte[] raw) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing from the runtime", e);
        }
    }

    private static Map<String, Object> benchOne(Map<String, Object> item, int repeat) throws UsageError {
        BigInteger n = decimal((String) item.get("n"), "moduli n");
        Map<String, Object> row = new TreeMap<>();
        row.put("label", String.valueOf(item.get("label")));
        row.put("bits", n.bitLength());
        row.put("repeat", repeat);
        long open = Long.MAX_VALUE, certify = Long.MAX_VALUE, verify = Long.MAX_VALUE, reveal = 0, product = 0;
        Verdict<PublicWorld> w = null;
        for (int i = 0; i < repeat; i++) {
            long s = System.nanoTime();
            w = PublicWorld.open(n, Telemetry.NONE);
            open = Math.min(open, System.nanoTime() - s);
        }
        if (w instanceof Verdict.Refused<PublicWorld> r) {
            row.put("verdict", r.toString());
            row.put("open_us", open / 1000);
            return row;
        }
        PublicWorld world = ((Verdict.Ok<PublicWorld>) w).value();
        String json = "";
        for (int i = 0; i < repeat; i++) {
            long s = System.nanoTime();
            json = Certificate.of(world).json();
            certify = Math.min(certify, System.nanoTime() - s);
            s = System.nanoTime();
            Certificate.verify(json, Telemetry.NONE);
            verify = Math.min(verify, System.nanoTime() - s);
        }
        PrimeTable primes = world.primes();
        long[] bits = new long[(primes.size() + 63) / 64];
        for (int i = 0; i < primes.size(); i++) if (Long.bitCount(i * 0x9E3779B97F4A7C15L) % 2 == 1) bits[i >>> 6] |= 1L << (i & 63);
        product = Long.MAX_VALUE;
        for (int i = 0; i < repeat; i++) {
            long s = System.nanoTime();
            Arith.subsetProduct(bits, primes, n);
            product = Math.min(product, System.nanoTime() - s);
        }
        if (item.get("p") instanceof String p) {
            reveal = Long.MAX_VALUE;
            for (int i = 0; i < repeat; i++) {
                long s = System.nanoTime();
                Verdict<GmWorld> g = GmWorld.reveal(world, decimal(p, "moduli p"), Optional.empty(), Telemetry.NONE);
                reveal = Math.min(reveal, System.nanoTime() - s);
                row.put("reveal", g instanceof Verdict.Refused<GmWorld> r ? r.toString() : "accepted");
            }
        }
        row.put("verdict", "accepted");
        row.put("g2", world.g2().value());
        row.put("budget", world.budget());
        row.put("primes", primes.size());
        row.put("cert_bytes", json.length());
        row.put("open_us", open / 1000);
        row.put("certify_us", certify / 1000);
        row.put("verify_us", verify / 1000);
        row.put("reveal_us", reveal / 1000);
        row.put("subset_product_us", product / 1000);
        return row;
    }
}
