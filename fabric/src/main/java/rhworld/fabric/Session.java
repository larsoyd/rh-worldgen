package rhworld.fabric;

import java.math.BigInteger;
import java.util.Optional;
import rhworld.mechanics.Warden;
import rhworld.mechanics.WildGate;

public record Session(BigInteger n, WildGate gate, Optional<Warden> warden) {
    private static volatile Session current;

    public static Session create(BigInteger n, Optional<Warden> warden) {
        return new Session(n, WildGate.forModulus(n), warden);
    }

    public static Optional<Session> current() {
        return Optional.ofNullable(current);
    }

    static void set(Session session) {
        current = session;
    }
}
