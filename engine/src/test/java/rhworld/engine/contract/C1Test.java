package rhworld.engine.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rhworld.engine.Golden;
import rhworld.engine.arith.Arith;
import rhworld.engine.arith.OddModulus;
import rhworld.engine.telemetry.Telemetry;

class C1Test {
    private static BigInteger big(Object o) {
        return new BigInteger((String) o);
    }

    @Test
    void matchesOracleOnEveryModulusCase() {
        List<Map<String, Object>> cases = Golden.list("modulus");
        assertTrue(cases.size() >= 13, "cases=" + cases.size());
        int accepted = 0;
        for (Map<String, Object> c : cases) {
            String label = (String) c.get("label");
            Verdict<C1.Admission> v = C1.admit(big(c.get("n")), Telemetry.NONE);
            if (c.containsKey("refused")) {
                assertEquals(c.get("refused"), v.toString(), label);
                continue;
            }
            accepted++;
            C1.Admission a = assertInstanceOf(Verdict.Ok.class, v, label).value() instanceof C1.Admission x ? x : null;
            assertEquals(((BigInteger) c.get("budget")).longValue(), a.budget(), label);
            assertEquals(((BigInteger) c.get("side")).longValue(), a.side(), label);
            Witness g2 = ((Verdict.Ok<Witness>) C1.g2(a, Telemetry.NONE)).value();
            assertEquals(((BigInteger) c.get("g2")).longValue(), g2.value(), label);
            assertEquals(g2.value(), g2.steps(), label);
            assertEquals(a.budget(), g2.bound(), label);
            @SuppressWarnings("unchecked")
            List<BigInteger> g1 = (List<BigInteger>) c.get("g1");
            @SuppressWarnings("unchecked")
            List<String> factors = (List<String>) c.get("factors");
            for (int i = 0; i < 2; i++) {
                Witness w = ((Verdict.Ok<Witness>) C1.g1(a, big(factors.get(i)), Telemetry.NONE)).value();
                assertEquals(g1.get(i).longValue(), w.value(), label + " factor " + i);
            }
        }
        assertEquals(8, accepted);
    }

    @Test
    void refusalOrderIsFixed() {
        BigInteger p = BigInteger.TWO.pow(521).subtract(BigInteger.ONE);
        BigInteger q = BigInteger.TWO.pow(607).subtract(BigInteger.ONE);
        assertEquals("refused: modulus too small or even", C1.admit(p.multiply(p).shiftLeft(2), Telemetry.NONE).toString());
        assertEquals("refused: jacobi character is principal", C1.admit(q.multiply(q), Telemetry.NONE).toString());
        assertEquals("refused: no hidden layer", C1.admit(BigInteger.TWO.pow(1279).subtract(BigInteger.ONE), Telemetry.NONE).toString());
        assertEquals("refused: small factor", C1.admit(p.multiply(q).multiply(BigInteger.valueOf(3)), Telemetry.NONE).toString());
        assertEquals("refused: modulus too small or even", C1.admit(p.multiply(BigInteger.valueOf(1L << 40).add(BigInteger.ONE)), Telemetry.NONE).toString());
    }

    @Test
    void trialDivisionCoversEveryPrimeUpToSideSquared() {
        BigInteger p = BigInteger.TWO.pow(1279).subtract(BigInteger.ONE);
        long side = rhworld.engine.arith.Budget.side(1279 + 20);
        long limit = side * side;
        long atLimit = largestPrimeAtMost(limit), above = largestPrimeAtMost(limit + 200) ;
        assertTrue(above > limit, "above=" + above);
        assertEquals("refused: small factor", C1.admit(p.multiply(BigInteger.valueOf(atLimit)), Telemetry.NONE).toString());
        assertInstanceOf(Verdict.Ok.class, C1.admit(p.multiply(BigInteger.valueOf(above)), Telemetry.NONE));
    }

    private static long largestPrimeAtMost(long x) {
        while (!Arith.isProbablePrime(BigInteger.valueOf(x))) x--;
        return x;
    }

    @Test
    void scanPastTheBoundIsRefused() {
        assertEquals("refused: budget exceeded", C1.scan(new OddModulus(BigInteger.valueOf(15)), 6, "g2", Telemetry.NONE).toString());
        assertEquals(new Verdict.Ok<>(new Witness(7, 7, 7)), C1.scan(new OddModulus(BigInteger.valueOf(15)), 7, "g2", Telemetry.NONE));
    }

    @Test
    void g1RefusesAFactorThatDoesNotDivide() {
        Map<String, Object> c = Golden.<Map<String, Object>>list("modulus").get(0);
        C1.Admission a = ((Verdict.Ok<C1.Admission>) C1.admit(big(c.get("n")), Telemetry.NONE)).value();
        assertEquals("refused: factor does not divide n", C1.g1(a, BigInteger.valueOf(1_000_003), Telemetry.NONE).toString());
    }

    @Test
    void admitReportsItsWork() {
        List<String> tags = new ArrayList<>();
        List<Map<String, Object>> fields = new ArrayList<>();
        Telemetry t = (tag, f) -> {
            tags.add(tag);
            fields.add(f);
        };
        BigInteger n = big(Golden.<Map<String, Object>>list("modulus").get(0).get("n"));
        C1.Admission a = ((Verdict.Ok<C1.Admission>) C1.admit(n, t)).value();
        C1.g2(a, t);
        assertEquals(List.of("c1.admit", "c1.g2"), tags);
        assertEquals(1024, fields.get(0).get("bits"));
        assertEquals(504100L, fields.get(0).get("trial_limit"));
        assertEquals(a.primesTested(), fields.get(0).get("primes_tested"));
        assertTrue(a.primesTested() > 40000, "primes=" + a.primesTested());
        assertEquals(3L, fields.get(1).get("witness"));
    }

    @Test
    void chainNamesTheLeanStatementAndThePaper() {
        assertEquals(new ContractId("RHW-C1", 1), C1.ID);
        assertEquals(Label.LEAN, C1.CHAIN.get(0).label());
        assertEquals("LFunction_ne_zero_of_half_lt_re", C1.CHAIN.get(0).ref());
        assertTrue(C1.CHAIN.get(1).ref().contains("Thm 1.1 (2)"), C1.CHAIN.get(1).ref());
        assertTrue(C1.HYPOTHESES.size() >= 3);
        assertEquals("RHW-C1 v1 derivation, see README Contracts", C1.CHAIN.get(2).ref());
        assertEquals("RHW-C3 v1 derivation, see README Contracts", C3.CHAIN.get(2).ref());
        for (TheoremRef r : List.of(C1.CHAIN.get(2), C3.CHAIN.get(2), C1.CHAIN.get(1), C1.CHAIN.get(0))) {
            assertTrue(!r.ref().contains(".md") && !r.statement().contains(".md"), r.toString());
        }
    }
}
