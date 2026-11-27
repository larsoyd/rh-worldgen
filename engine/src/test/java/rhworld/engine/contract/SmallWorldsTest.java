package rhworld.engine.contract;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import rhworld.engine.arith.Budget;
import rhworld.engine.arith.OddModulus;
import rhworld.engine.telemetry.Telemetry;

class SmallWorldsTest {
    static final int LIMIT = 1_000_000;
    static final int[] SPF = smallestPrimeFactors(LIMIT);

    static int[] smallestPrimeFactors(int n) {
        int[] spf = new int[n + 1];
        for (int i = 2; i <= n; i++) {
            if (spf[i] != 0) continue;
            for (int j = i; j <= n; j += i) if (spf[j] == 0) spf[j] = i;
        }
        return spf;
    }

    static int legendre(long a, long p) {
        long r = BigInteger.valueOf(Math.floorMod(a, p)).modPow(BigInteger.valueOf((p - 1) / 2), BigInteger.valueOf(p)).longValueExact();
        return r == p - 1 ? -1 : (int) r;
    }

    static int bruteJacobi(long a, long n) {
        int s = 1;
        for (long m = n; m > 1; m /= SPF[(int) m]) s *= legendre(a, SPF[(int) m]);
        return s;
    }

    static List<Integer> factors(int n) {
        List<Integer> out = new ArrayList<>();
        for (int m = n; m > 1; m /= SPF[m]) out.add(SPF[m]);
        return out;
    }

    static boolean admissible(int n) {
        int bits = 32 - Integer.numberOfLeadingZeros(n);
        long b = Budget.forBits(bits), s = Budget.side(bits);
        long r = (long) Math.sqrt(n);
        return n % 2 == 1 && r * r != n && (r + 1) * (r + 1) != n && SPF[n] != n && SPF[n] > Math.max(b, s * s);
    }

    record Count(int admitted, int refused, long worstWitness, int worstN) {}

    @Test
    void c1AdmissionAndBothWitnessesAgreeWithBruteForceOnEverySmallModulus() {
        int admitted = 0, refused = 0, worstN = 0;
        long worst = 0;
        for (int n = 3001; n <= LIMIT; n++) {
            Verdict<C1.Admission> v = C1.admit(BigInteger.valueOf(n), 2, Telemetry.NONE);
            assertEquals(admissible(n), v instanceof Verdict.Ok<?>, "n=" + n + " " + v);
            if (!(v instanceof Verdict.Ok<C1.Admission> ok)) {
                refused++;
                continue;
            }
            admitted++;
            C1.Admission a = ok.value();
            long want = 1;
            while (bruteJacobi(want, n) != -1) want++;
            Witness g2 = ((Verdict.Ok<Witness>) C1.g2(a, Telemetry.NONE)).value();
            assertEquals(want, g2.value(), "g2 n=" + n);
            assertTrue(want <= a.budget(), "g2 beyond budget n=" + n);
            if (want > worst) {
                worst = want;
                worstN = n;
            }
            for (int p : new java.util.TreeSet<>(factors(n))) {
                long w = 1;
                while (legendre(w, p) != -1) w++;
                assertEquals(w, ((Verdict.Ok<Witness>) C1.g1(a, BigInteger.valueOf(p), Telemetry.NONE)).value().value(), "g1 n=" + n + " p=" + p);
            }
        }
        System.out.println("[small-worlds] contract=c1 range=3001.." + LIMIT + " admitted=" + admitted + " refused=" + refused
                + " worst_g2=" + worst + " at_n=" + worstN);
        assertTrue(admitted > 20_000, "admitted=" + admitted);
    }

    @Test
    void aBudgetOfLnNIsTooSmallSomewhereInRange() {
        int first = 0;
        for (int n = 3001; n <= LIMIT && first == 0; n++) {
            if (!admissible(n)) continue;
            long cut = (long) Math.floor(Math.log(n));
            if (C1.scan(new OddModulus(BigInteger.valueOf(n)), cut, "c1.g2", Telemetry.NONE) instanceof Verdict.Refused<?>) first = n;
        }
        System.out.println("[small-worlds] control=ln_n_budget first_refused_n=" + first);
        assertTrue(first > 0, "a budget of ln n never failed in range");
    }

    @Test
    void c3BasisAndEveryRouteAgreeWithBruteForceOnEverySmallSemiprime() {
        int checked = 0;
        for (int n = 3001; n <= LIMIT; n += 2) {
            List<Integer> f = factors(n);
            if (f.size() != 2 || f.get(0).equals(f.get(1))) continue;
            List<BigInteger> layers = List.of(BigInteger.valueOf(f.get(0)), BigInteger.valueOf(f.get(1)));
            long bound = Budget.forBits(32 - Integer.numberOfLeadingZeros(n));
            Verdict<C3.Basis> v = C3.basis(layers, Telemetry.NONE);
            if (f.get(0) <= bound) {
                assertEquals("refused: small factor", v.toString(), "n=" + n);
                continue;
            }
            C3.Basis b = ((Verdict.Ok<C3.Basis>) v).value();
            List<Integer> kept = new ArrayList<>();
            int span = 1;
            long steps = 0;
            for (int l = 2; kept.size() < 2; l++) {
                if (SPF[l] != l) continue;
                steps++;
                int x = (legendre(l, f.get(0)) == -1 ? 1 : 0) | (legendre(l, f.get(1)) == -1 ? 2 : 0);
                if ((span >>> x & 1) == 0) {
                    kept.add(l);
                    int next = span;
                    for (int s = 0; s < 4; s++) if ((span >>> s & 1) == 1) next |= 1 << (s ^ x);
                    span = next;
                }
            }
            assertArrayEquals(kept.stream().mapToInt(Integer::intValue).toArray(), b.primes(), "n=" + n);
            assertEquals(steps, b.steps(), "n=" + n);
            assertTrue(kept.get(1) <= bound, "basis beyond budget n=" + n);
            for (int from = 0; from < 4; from++) {
                for (int to = 0; to < 4; to++) {
                    int s = from;
                    for (int l : b.route(from, to)) s ^= (legendre(l, f.get(0)) == -1 ? 1 : 0) | (legendre(l, f.get(1)) == -1 ? 2 : 0);
                    assertEquals(to, s, "n=" + n + " from=" + from + " to=" + to);
                }
            }
            checked++;
        }
        System.out.println("[small-worlds] contract=c3 range=3001.." + LIMIT + " semiprimes_checked=" + checked);
        assertTrue(checked > 20_000, "checked=" + checked);
    }
}
