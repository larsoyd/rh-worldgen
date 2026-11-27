package rhworld.engine.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import rhworld.engine.arith.Arith;
import rhworld.engine.cert.Certificate;
import rhworld.engine.contract.Verdict;
import rhworld.engine.telemetry.Telemetry;

class ConcurrencyTest {
    static final int QUERIES = 6000;

    static BigInteger[] queries(BigInteger n) {
        Random r = new Random(7);
        BigInteger[] xs = new BigInteger[QUERIES];
        for (int i = 0; i < QUERIES; i++) {
            xs[i] = switch (i % 4) {
                case 0 -> BigInteger.valueOf(1 + i);
                case 1 -> new BigInteger(n.bitLength() - 1, r);
                case 2 -> new BigInteger(8192, r);
                default -> n.multiply(BigInteger.valueOf(i)).add(BigInteger.valueOf(i % 97 + 1));
            };
        }
        return xs;
    }

    static String answer(PublicWorld w, GmWorld g, BigInteger x) {
        Verdict<Realm> r = g.realm(x);
        return w.jacobi(x) + ":" + (r instanceof Verdict.Ok<Realm> ok ? ok.value().state() : r.toString());
    }

    static String digest(String[] answers) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (String a : answers) md.update((a + "\n").getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(md.digest());
    }

    static String run(int threads, PublicWorld w, GmWorld g, BigInteger[] xs) throws Exception {
        String[] out = new String[xs.length];
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> jobs = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int me = t;
                jobs.add(pool.submit(() -> {
                    for (int i = xs.length - 1 - me; i >= 0; i -= threads) out[i] = answer(w, g, xs[i]);
                }));
            }
            for (Future<?> f : jobs) f.get(120, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        return digest(out);
    }

    @Test
    void answersAreIdenticalForEveryThreadCountAndOrder() throws Exception {
        Map<String, Object> c = WorldTest.accepted().get(3);
        PublicWorld w = WorldTest.ok(PublicWorld.open(WorldTest.big(c.get("n")), Telemetry.NONE));
        GmWorld g = WorldTest.ok(GmWorld.reveal(w, WorldTest.factor(c, 0), Optional.empty(), Telemetry.NONE));
        BigInteger[] xs = queries(w.n());
        String[] sequential = new String[xs.length];
        for (int i = 0; i < xs.length; i++) {
            BigInteger x = xs[i];
            int a = Arith.jacobi(x, g.p()), b = Arith.jacobi(x, g.q());
            sequential[i] = Arith.jacobi(x, w.n()) + ":" + (a == 0 || b == 0 ? "refused: not a unit" : new Realm(a, b).state());
        }
        String want = digest(sequential);
        for (int threads : new int[] {1, 2, 3, 8, 32}) assertEquals(want, run(threads, w, g, xs), "threads=" + threads);
    }

    @Test
    void worldsOpenedConcurrentlyGiveIdenticalCertificates() throws Exception {
        List<Map<String, Object>> cases = WorldTest.accepted();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            for (int k : new int[] {0, 3, 7}) {
                BigInteger n = WorldTest.big(cases.get(k).get("n")), p = WorldTest.factor(cases.get(k), 1);
                String want = Certificate.of(WorldTest.ok(GmWorld.reveal(WorldTest.ok(PublicWorld.open(n, Telemetry.NONE)), p, Optional.empty(),
                        Telemetry.NONE))).json();
                List<Future<String>> jobs = new ArrayList<>();
                for (int t = 0; t < 32; t++) {
                    jobs.add(pool.submit(() -> Certificate.of(WorldTest.ok(GmWorld.reveal(WorldTest.ok(PublicWorld.open(n, Telemetry.NONE)), p,
                            Optional.empty(), Telemetry.NONE))).json()));
                }
                for (Future<String> f : jobs) assertEquals(want, f.get(120, TimeUnit.SECONDS), "case " + k);
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void queriesAreLazyAndNeedNoPrecomputationBeyondThePrimeTable() {
        Map<String, Object> c = WorldTest.accepted().get(4);
        PublicWorld w = WorldTest.ok(PublicWorld.open(WorldTest.big(c.get("n")), Telemetry.NONE));
        GmWorld g = WorldTest.ok(GmWorld.reveal(w, WorldTest.factor(c, 0), Optional.empty(), Telemetry.NONE));
        BigInteger far = BigInteger.TWO.pow(1 << 16).add(BigInteger.valueOf(12345));
        assertEquals(Arith.jacobi(far, w.n()), w.jacobi(far));
        assertEquals(Arith.jacobi(far.mod(w.n()), g.p()), ((Verdict.Ok<Realm>) g.realm(far)).value().p());
        long[] sizes = retained(w);
        assertEquals(w.primes().size(), sizes[0], "the prime table is the only array state");
        assertTrue(sizes[1] == 0, "no collections held");
    }

    static long[] retained(Object o) {
        long array = 0, collections = 0;
        java.util.ArrayDeque<Object> todo = new java.util.ArrayDeque<>(List.of(o));
        java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (!todo.isEmpty()) {
            Object x = todo.pop();
            if (x == null || !seen.add(x) || x instanceof BigInteger || x instanceof String || x instanceof Number) continue;
            if (x instanceof int[] a) {
                array = Math.max(array, a.length);
                continue;
            }
            if (x instanceof java.util.Collection<?> || x instanceof Map<?, ?>) collections++;
            if (!x.getClass().getName().startsWith("rhworld.")) continue;
            for (Class<?> k = x.getClass(); k != null; k = k.getSuperclass()) {
                for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    f.setAccessible(true);
                    try {
                        todo.push(f.get(x));
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
        return new long[] {array, collections};
    }
}
