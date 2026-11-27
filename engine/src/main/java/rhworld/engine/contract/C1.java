package rhworld.engine.contract;

import java.math.BigInteger;
import java.util.List;
import java.util.OptionalLong;
import rhworld.engine.arith.Arith;
import rhworld.engine.arith.Budget;
import rhworld.engine.arith.OddModulus;
import rhworld.engine.arith.Primes;
import rhworld.engine.telemetry.Telemetry;

public final class C1 {
    public static final ContractId ID = new ContractId("RHW-C1", 1);
    public static final int MIN_BITS = 1024;
    public static final List<TheoremRef> CHAIN = List.of(
            new TheoremRef(Label.LEAN, "LFunction_ne_zero_of_half_lt_re",
                    "DirichletLNonvanishingRightOfHalf: L(s, chi) != 0 for every N, every Dirichlet character chi mod N, re s > 1/2,"
                            + " except chi = 1 at s = 1 (riemann-hypothesis main 7c4ccbd64d02774b0a5b40a5f923f5b7a18e8abc, src/Main.lean:17)"),
            new TheoremRef(Label.PUB, "Lamzouri, Li, Soundararajan, Math. Comp. 84 (2015), arXiv 1309.3595v3, Thm 1.1 (2)",
                    "Assume GRH. Let q >= 3000 and H a proper subgroup of (Z/qZ)*. If q is not divisible by any prime below (log q)^2,"
                            + " then some prime l <= (log q)^2 does not lie in H."),
            new TheoremRef(Label.NEW, "RHW-C1 v1 derivation, see README Contracts",
                    "with q = n and H the kernel of (./n), (./p) or (./q), the least x with symbol -1 is an integer <= floor((ln n)^2) <= B(k)"));
    public static final List<Hypothesis> HYPOTHESES = List.of(
            new Hypothesis("q = n >= 3000", "recomputed.c1.bits"),
            new Hypothesis("no prime factor of n at most floor((ln n)^2)", "recomputed.c1.trial_limit"),
            new Hypothesis("H = ker (./n) is proper: n is not a perfect square", "recomputed.c1.square"),
            new Hypothesis("H = ker (./p) and ker (./q) are proper: p, q distinct primes", "deferred.reveal.factors_status"));

    public record Admission(BigInteger n, int bits, long budget, long side, long trialLimit, long primesTested) {}

    private C1() {}

    public static Verdict<Admission> admit(BigInteger n, Telemetry t) {
        long start = System.nanoTime();
        int bits = n.bitLength();
        Verdict<Admission> v = check(n, bits);
        switch (v) {
            case Verdict.Ok<Admission> ok -> t.event("c1.admit", "bits", bits, "budget", ok.value().budget(), "trial_limit",
                    ok.value().trialLimit(), "primes_tested", ok.value().primesTested(), "us", (System.nanoTime() - start) / 1000);
            case Verdict.Refused<Admission> r -> t.event("c1.refused", "bits", bits, "reason", r.reason());
        }
        return v;
    }

    private static Verdict<Admission> check(BigInteger n, int bits) {
        if (!n.testBit(0) || n.signum() <= 0 || bits < MIN_BITS) return new Verdict.Refused<>("modulus too small or even");
        if (Arith.isPerfectSquare(n)) return new Verdict.Refused<>("jacobi character is principal");
        if (Arith.isProbablePrime(n)) return new Verdict.Refused<>("no hidden layer");
        long budget = Budget.forBits(bits), side = Budget.side(bits);
        long limit = Math.max(budget, side * side);
        if (Arith.leastFactor(n, limit).isPresent()) return new Verdict.Refused<>("small factor");
        return new Verdict.Ok<>(new Admission(n, bits, budget, side, limit, Primes.upTo(limit).length));
    }

    public static Verdict<Witness> g2(Admission a, Telemetry t) {
        return scan(new OddModulus(a.n()), a.budget(), "c1.g2", t);
    }

    public static Verdict<Witness> g1(Admission a, BigInteger factor, Telemetry t) {
        if (factor.compareTo(BigInteger.ONE) <= 0 || factor.compareTo(a.n()) >= 0 || a.n().mod(factor).signum() != 0) {
            return new Verdict.Refused<>("factor does not divide n");
        }
        return scan(new OddModulus(factor), a.budget(), "c1.g1", t);
    }

    static Verdict<Witness> scan(OddModulus m, long bound, String tag, Telemetry t) {
        OptionalLong w = m.leastNonResidue(bound);
        if (w.isEmpty()) {
            t.event(tag, "bound", bound, "reason", "budget exceeded");
            return new Verdict.Refused<>("budget exceeded");
        }
        t.event(tag, "witness", w.getAsLong(), "bound", bound);
        return new Verdict.Ok<>(new Witness(w.getAsLong(), w.getAsLong(), bound));
    }
}
