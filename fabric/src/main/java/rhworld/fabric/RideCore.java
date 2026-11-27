package rhworld.fabric;

import java.math.BigInteger;
import java.util.Optional;
import rhworld.mechanics.Colour;
import rhworld.mechanics.Dice;
import rhworld.mechanics.Ride;

public final class RideCore {
    public static final String NO_KEY = "refused: no operator key on this server";
    public static final String NO_SEED = "refused: no dice seed, set one with /rhworld dice <64 hex digits>";

    public record Outcome(Optional<Ride> ride, Optional<String> refusal) {
        static Outcome refused(String reason) {
            return new Outcome(Optional.empty(), Optional.of(reason));
        }
    }

    private RideCore() {}

    public static Outcome ride(Session s, BigInteger x, Colour colour, byte[] seed, long index) {
        if (s.warden().isEmpty()) return Outcome.refused(NO_KEY);
        if (seed == null) return Outcome.refused(NO_SEED);
        try {
            long[] e = Dice.bits(seed, index, s.gate().size());
            BigInteger after = s.gate().ride(x, colour, e);
            var realm = s.warden().get().realm(after);
            return new Outcome(Optional.of(new Ride(index, colour, x, after, realm)), Optional.empty());
        } catch (IllegalArgumentException | IllegalStateException e) {
            String m = String.valueOf(e.getMessage());
            return Outcome.refused(m.startsWith("refused:") ? m : "refused: " + m);
        }
    }
}
