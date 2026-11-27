package rhworld.engine.world;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import rhworld.engine.arith.Arith;
import rhworld.engine.arith.Pocklington;
import rhworld.engine.contract.C1;
import rhworld.engine.contract.C3;
import rhworld.engine.contract.Verdict;
import rhworld.engine.contract.Witness;
import rhworld.engine.telemetry.Telemetry;

public final class GmWorld {
    public record Proofs(Map<String, Object> p, Map<String, Object> q) {}

    private final PublicWorld world;
    private final BigInteger p, q;
    private final String factorsStatus;
    private final Witness g1p, g1q;
    private final C3.Basis hiddenBasis;

    private GmWorld(PublicWorld world, BigInteger p, BigInteger q, String status, Witness g1p, Witness g1q, C3.Basis basis) {
        this.world = world;
        this.p = p;
        this.q = q;
        this.factorsStatus = status;
        this.g1p = g1p;
        this.g1q = g1q;
        this.hiddenBasis = basis;
    }

    public static Verdict<GmWorld> reveal(PublicWorld w, BigInteger factor, Optional<Proofs> proofs, Telemetry t) {
        Verdict<GmWorld> v = check(w, factor, proofs, t);
        if (v instanceof Verdict.Refused<GmWorld> r) t.event("reveal.refused", "reason", r.reason());
        else t.event("reveal", "factors_status", ((Verdict.Ok<GmWorld>) v).value().factorsStatus());
        return v;
    }

    private static Verdict<GmWorld> check(PublicWorld w, BigInteger factor, Optional<Proofs> proofs, Telemetry t) {
        BigInteger n = w.n();
        if (factor.compareTo(BigInteger.ONE) <= 0 || factor.compareTo(n) >= 0 || n.mod(factor).signum() != 0) {
            return new Verdict.Refused<>("revealed factor does not divide n");
        }
        BigInteger cofactor = n.divide(factor);
        if (cofactor.equals(factor)) return new Verdict.Refused<>("factors equal");
        if (!Arith.isProbablePrime(factor)) return new Verdict.Refused<>("revealed factor not prime");
        if (!Arith.isProbablePrime(cofactor)) return new Verdict.Refused<>("cofactor not prime");
        BigInteger p = factor.min(cofactor), q = factor.max(cofactor);
        String status = "mr25-probable";
        if (proofs.isPresent()) {
            if (Pocklington.check(p, proofs.get().p()) instanceof Verdict.Refused<String> r) return new Verdict.Refused<>("proof for p: " + r.reason());
            if (Pocklington.check(q, proofs.get().q()) instanceof Verdict.Refused<String> r) return new Verdict.Refused<>("proof for q: " + r.reason());
            status = "proved";
        }
        String s = status;
        return C1.g1(w.admission(), p, t).then(a -> C1.g1(w.admission(), q, t)
                .then(b -> C3.basis(List.of(p, q), t).map(basis -> new GmWorld(w, p, q, s, a, b, basis))));
    }

    public PublicWorld world() {
        return world;
    }

    public BigInteger p() {
        return p;
    }

    public BigInteger q() {
        return q;
    }

    public String factorsStatus() {
        return factorsStatus;
    }

    public Witness g1p() {
        return g1p;
    }

    public Witness g1q() {
        return g1q;
    }

    public C3.Basis hiddenBasis() {
        return hiddenBasis;
    }

    public Verdict<Realm> realm(BigInteger x) {
        int a = Arith.jacobi(x, p), b = Arith.jacobi(x, q);
        if (a == 0 || b == 0) return new Verdict.Refused<>("not a unit");
        return new Verdict.Ok<>(new Realm(a, b));
    }
}
