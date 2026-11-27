package rhworld.engine.contract;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import rhworld.engine.arith.Arith;
import rhworld.engine.arith.Budget;
import rhworld.engine.arith.OddModulus;
import rhworld.engine.arith.Primes;
import rhworld.engine.telemetry.Telemetry;

public final class C3 {
    public static final ContractId ID = new ContractId("RHW-C3", 1);
    public static final int MAX_LAYERS = 8;
    public static final List<TheoremRef> CHAIN = List.of(
            C1.CHAIN.get(0),
            C1.CHAIN.get(1),
            new TheoremRef(Label.NEW, "RHW-C3 v1, L-SPAN",
                    "if the span V of the kept images is proper, phi^-1(V) is a proper subgroup of (Z/M)*, so a prime l <= (ln M)^2 <= B"
                            + " lies outside it. The primes <= B span F2^r and every state is reached in at most r hops"));
    public static final List<Hypothesis> HYPOTHESES = List.of(
            new Hypothesis("M = lcm of the layers >= 3000", "recomputed.c3.lcm_bits"),
            new Hypothesis("no prime factor of any layer at most B = floor((ln 2 bitlen M)^2)", "recomputed.c3.bound"),
            new Hypothesis("phi onto F2^r: no nonempty product of layers is a perfect square", "recomputed.c3.independent"),
            new Hypothesis("hidden layers are the distinct prime factors of n", "deferred.reveal.factors_status"));

    public record Basis(int[] primes, int[] vectors, int rank, long steps, long bound) {
        public Basis {
            primes = primes.clone();
            vectors = vectors.clone();
        }

        @Override
        public int[] primes() {
            return primes.clone();
        }

        @Override
        public int[] vectors() {
            return vectors.clone();
        }

        public int[] route(int from, int to) {
            int target = from ^ to, best = -1;
            for (int c = 0; c < 1 << rank && best < 0; c++) {
                int s = 0;
                for (int j = 0; j < rank; j++) if ((c >>> j & 1) == 1) s ^= vectors[j];
                if (s == target) best = c;
            }
            if (best < 0) throw new IllegalArgumentException("state out of range from=" + from + " to=" + to + " rank=" + rank);
            int[] hops = new int[Integer.bitCount(best)];
            for (int j = 0, k = 0; j < rank; j++) if ((best >>> j & 1) == 1) hops[k++] = primes[j];
            return hops;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Basis b && Arrays.equals(primes, b.primes) && Arrays.equals(vectors, b.vectors) && rank == b.rank
                    && steps == b.steps && bound == b.bound;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(primes) * 31 + Arrays.hashCode(vectors);
        }

        @Override
        public String toString() {
            return "Basis[primes=" + Arrays.toString(primes) + ", vectors=" + Arrays.toString(vectors) + ", rank=" + rank + ", steps=" + steps
                    + ", bound=" + bound + "]";
        }
    }

    private C3() {}

    public static Verdict<Basis> basis(List<BigInteger> layers, Telemetry t) {
        Verdict<Long> admitted = admit(layers);
        if (admitted instanceof Verdict.Refused<Long> r) {
            t.event("c3.refused", "layers", layers.size(), "reason", r.reason());
            return new Verdict.Refused<>(r.reason());
        }
        return scan(layers, ((Verdict.Ok<Long>) admitted).value(), t);
    }

    private static Verdict<Long> admit(List<BigInteger> layers) {
        int r = layers.size();
        if (r == 0) return new Verdict.Refused<>("no layers");
        if (r > MAX_LAYERS) return new Verdict.Refused<>("too many layers");
        for (BigInteger n : layers) {
            if (n.compareTo(BigInteger.valueOf(3)) < 0 || !n.testBit(0)) return new Verdict.Refused<>("modulus too small or even");
        }
        for (int mask = 1; mask < 1 << r; mask++) {
            BigInteger prod = BigInteger.ONE;
            for (int i = 0; i < r; i++) if ((mask >>> i & 1) == 1) prod = prod.multiply(layers.get(i));
            if (Arith.isPerfectSquare(prod)) return new Verdict.Refused<>("dependent layers");
        }
        BigInteger lcm = BigInteger.ONE;
        for (BigInteger n : layers) lcm = lcm.divide(lcm.gcd(n)).multiply(n);
        if (lcm.compareTo(BigInteger.valueOf(3000)) < 0) return new Verdict.Refused<>("modulus too small or even");
        long bound = Budget.forBits(lcm.bitLength());
        for (BigInteger n : layers) if (Arith.leastFactor(n, bound).isPresent()) return new Verdict.Refused<>("small factor");
        return new Verdict.Ok<>(bound);
    }

    static Verdict<Basis> scan(List<BigInteger> layers, long bound, Telemetry t) {
        int r = layers.size();
        OddModulus[] mods = layers.stream().map(OddModulus::new).toArray(OddModulus[]::new);
        int[] kept = new int[r], vectors = new int[r], reduced = new int[r];
        int rank = 0;
        long steps = 0;
        for (int l : Primes.upTo(bound)) {
            steps++;
            int v = 0;
            for (int i = 0; i < r; i++) if (mods[i].jacobi(l) == -1) v |= 1 << i;
            int x = v;
            for (int j = 0; j < rank; j++) x = Math.min(x, x ^ reduced[j]);
            if (x == 0) continue;
            kept[rank] = l;
            vectors[rank] = v;
            int at = rank++;
            for (; at > 0 && reduced[at - 1] < x; at--) reduced[at] = reduced[at - 1];
            reduced[at] = x;
            if (rank == r) {
                t.event("c3.basis", "layers", r, "rank", rank, "steps", steps, "last", l, "bound", bound);
                return new Verdict.Ok<>(new Basis(kept, vectors, rank, steps, bound));
            }
        }
        t.event("c3.basis", "layers", r, "rank", rank, "steps", steps, "bound", bound, "reason", "budget exceeded");
        return new Verdict.Refused<>("budget exceeded");
    }
}
