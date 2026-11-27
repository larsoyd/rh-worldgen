package rhworld.engine.world;

import java.math.BigInteger;
import java.util.List;
import rhworld.engine.arith.Arith;
import rhworld.engine.contract.C1;
import rhworld.engine.contract.C3;
import rhworld.engine.contract.Verdict;
import rhworld.engine.contract.Witness;
import rhworld.engine.telemetry.Telemetry;

public final class PublicWorld {
    private final C1.Admission admission;
    private final Witness g2;
    private final C3.Basis basis;

    private PublicWorld(C1.Admission admission, Witness g2, C3.Basis basis) {
        this.admission = admission;
        this.g2 = g2;
        this.basis = basis;
    }

    public static Verdict<PublicWorld> open(BigInteger n, Telemetry t) {
        return C1.admit(n, t).then(a -> C1.g2(a, t)
                .then(g2 -> C3.basis(List.of(n), t).map(b -> new PublicWorld(a, g2, b))));
    }

    public BigInteger n() {
        return admission.n();
    }

    public int bits() {
        return admission.bits();
    }

    public long budget() {
        return admission.budget();
    }

    public C1.Admission admission() {
        return admission;
    }

    public Witness g2() {
        return g2;
    }

    public C3.Basis basis() {
        return basis;
    }

    public int jacobi(BigInteger x) {
        return Arith.jacobi(x, admission.n());
    }
}
