package rhworld.engine.cert;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import rhworld.engine.contract.C1;
import rhworld.engine.contract.C3;
import rhworld.engine.contract.ContractId;
import rhworld.engine.contract.Hypothesis;
import rhworld.engine.contract.TheoremRef;
import rhworld.engine.contract.Verdict;
import rhworld.engine.contract.Witness;
import rhworld.engine.json.Json;
import rhworld.engine.json.JsonException;
import rhworld.engine.telemetry.Telemetry;
import rhworld.engine.world.GmWorld;
import rhworld.engine.world.PublicWorld;

public final class Certificate {
    public static final String SCHEMA = "rhworld.cert/1";
    public static final String ENGINE_VERSION = "0.1.0";
    public static final String PRIMALITY_TEST = "mr25-fixed-bases";
    public static final List<String> NOT_PROVED = List.of(
            "GRH is not checked by this certificate. It is the hypothesis of the cited paper, machine-checked only as the Lean statement named in theorem",
            "the explicit bound (log q)^2 is a published result and is not machine-checked",
            "claims about the hidden factors hold only after a reveal that passes the reveal check",
            "probable prime means the named fixed-base test passed, not a proof, unless factors_status is proved",
            "nothing is claimed about any window other than the prefix 1..B",
            "hiding the factors rests on the hardness of factoring, not on RH");

    public record Report(String worldId, boolean revealed, String factorsStatus) {}

    private final Map<String, Object> root;
    private final String json;
    private final String worldId;

    private Certificate(Map<String, Object> root) {
        this.root = root;
        this.json = Json.write(root);
        Map<String, Object> id = new LinkedHashMap<>();
        id.put("header", root.get("header"));
        id.put("recomputed", root.get("recomputed"));
        this.worldId = sha256(Json.write(id));
    }

    public static Certificate of(PublicWorld w) {
        return new Certificate(build(w, null));
    }

    public static Certificate of(GmWorld g) {
        return new Certificate(build(g.world(), g));
    }

    public String json() {
        return json;
    }

    public String worldId() {
        return worldId;
    }

    public static Verdict<Report> verify(String text, Telemetry t) {
        Map<String, Object> m;
        BigInteger n;
        Optional<Map<String, Object>> reveal;
        try {
            m = asMap(Json.parse(text));
            Map<String, Object> header = asMap(m.get("header"));
            if (!SCHEMA.equals(header.get("schema"))) return refuse(t, "unsupported schema");
            n = new BigInteger((String) asMap(header.get("params")).get("n"));
            Object r = asMap(m.get("deferred")).get("reveal");
            reveal = r == null ? Optional.empty() : Optional.of(asMap(r));
        } catch (JsonException | ClassCastException | NullPointerException | NumberFormatException e) {
            return refuse(t, "certificate malformed");
        }
        Verdict<PublicWorld> w = PublicWorld.open(n, t);
        if (w instanceof Verdict.Refused<PublicWorld> r) return refuse(t, r.reason());
        PublicWorld world = ((Verdict.Ok<PublicWorld>) w).value();
        Certificate want;
        if (reveal.isPresent()) {
            Verdict<GmWorld> g = revealFrom(world, reveal.get(), t);
            if (g instanceof Verdict.Refused<GmWorld> r) return refuse(t, r.reason());
            want = of(((Verdict.Ok<GmWorld>) g).value());
        } else {
            want = of(world);
        }
        Optional<String> diff = firstDifference("", want.root, m);
        if (diff.isPresent()) return refuse(t, "field mismatch " + diff.get());
        String status = reveal.map(r -> (String) r.get("factors_status")).orElse("unrevealed");
        t.event("cert.verify", "world_id", want.worldId, "revealed", reveal.isPresent(), "factors_status", status);
        return new Verdict.Ok<>(new Report(want.worldId, reveal.isPresent(), status));
    }

    @SuppressWarnings("unchecked")
    private static Verdict<GmWorld> revealFrom(PublicWorld w, Map<String, Object> reveal, Telemetry t) {
        try {
            BigInteger p = new BigInteger((String) reveal.get("p"));
            Optional<GmWorld.Proofs> proofs = Optional.ofNullable((Map<String, Object>) reveal.get("proofs"))
                    .map(x -> new GmWorld.Proofs(asMap(x.get("p")), asMap(x.get("q"))));
            return GmWorld.reveal(w, p, proofs, t);
        } catch (ClassCastException | NullPointerException | NumberFormatException e) {
            return new Verdict.Refused<>("certificate malformed");
        }
    }

    private static Verdict<Report> refuse(Telemetry t, String reason) {
        t.event("cert.refused", "reason", reason);
        return new Verdict.Refused<>(reason);
    }

    private static Map<String, Object> build(PublicWorld w, GmWorld g) {
        C1.Admission a = w.admission();
        Map<String, Object> header = map("schema", SCHEMA, "engine_version", ENGINE_VERSION,
                "contracts", List.of(contract(C1.ID), contract(C3.ID)), "params", map("n", w.n().toString(), "bits", w.bits()));
        Map<String, Object> c1 = map("bits", a.bits(), "min_bits", C1.MIN_BITS, "square", false, "probable_prime", false,
                "trial_limit", a.trialLimit(), "primes_tested", a.primesTested(), "budget", a.budget(), "side", a.side(),
                "g2", witness(w.g2()));
        Map<String, Object> c3 = map("layers", List.of("n"), "lcm_bits", w.bits(), "independent", true, "bound", w.basis().bound(),
                "basis", basis(w.basis()));
        Map<String, Object> recomputed = map("budget_exact", true, "primality_test", PRIMALITY_TEST, "c1", c1, "c3", c3);
        Map<String, Object> theorem = map(C1.ID.name(), theorem(C1.CHAIN, C1.HYPOTHESES,
                        "a least x <= B with (x/n) = -1 exists, and for each prime factor f of n a least x <= B with (x/f) = -1 exists"),
                C3.ID.name(), theorem(C3.CHAIN, C3.HYPOTHESES,
                        "the primes <= B span the image of phi in F2^r, so every state is reached in at most r prime steps with labels <= B"));
        Map<String, Object> deferred = new LinkedHashMap<>();
        deferred.put("reveal", g == null ? null : reveal(g));
        return map("header", header, "recomputed", recomputed, "theorem", theorem, "deferred", deferred, "not_proved", NOT_PROVED);
    }

    private static Map<String, Object> reveal(GmWorld g) {
        Map<String, Object> r = map("p", g.p().toString(), "q", g.q().toString(), "factors_status", g.factorsStatus(),
                "primality_test", PRIMALITY_TEST, "g1", map("p", witness(g.g1p()), "q", witness(g.g1q())),
                "c3_hidden", map("layers", List.of("p", "q"), "bound", g.hiddenBasis().bound(), "basis", basis(g.hiddenBasis())));
        g.proofs().ifPresent(pr -> r.put("proofs", map("p", pr.p(), "q", pr.q())));
        return r;
    }

    private static Map<String, Object> contract(ContractId id) {
        return map("id", id.name(), "version", id.version());
    }

    private static Map<String, Object> witness(Witness w) {
        return map("witness", w.value(), "steps", w.steps(), "bound", w.bound());
    }

    private static Map<String, Object> basis(C3.Basis b) {
        return map("primes", Arrays.stream(b.primes()).boxed().toList(), "vectors", Arrays.stream(b.vectors()).boxed().toList(),
                "rank", b.rank(), "steps", b.steps());
    }

    private static Map<String, Object> theorem(List<TheoremRef> chain, List<Hypothesis> hyps, String claim) {
        List<Object> c = new ArrayList<>();
        for (TheoremRef r : chain) c.add(map("label", r.label().name(), "ref", r.ref(), "statement", r.statement()));
        List<Object> h = new ArrayList<>();
        for (Hypothesis x : hyps) h.add(map("statement", x.statement(), "checked_by", x.checkedBy()));
        return map("chain", c, "hypotheses", h, "claim", claim, "claim_scope", "prefix 1..B");
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (!(o instanceof Map<?, ?> m)) throw new ClassCastException("not an object");
        return (Map<String, Object>) m;
    }

    private static Optional<String> firstDifference(String path, Object want, Object got) {
        if (want instanceof Map<?, ?> a && got instanceof Map<?, ?> b) {
            TreeSet<String> keys = new TreeSet<>();
            a.keySet().forEach(k -> keys.add((String) k));
            b.keySet().forEach(k -> keys.add((String) k));
            for (String k : keys) {
                Optional<String> d = firstDifference(path.isEmpty() ? k : path + "." + k, a.get(k), b.get(k));
                if (d.isPresent()) return d;
            }
            return Optional.empty();
        }
        return Json.write(want).equals(Json.write(got)) ? Optional.empty() : Optional.of(path);
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing from the runtime", e);
        }
    }
}
