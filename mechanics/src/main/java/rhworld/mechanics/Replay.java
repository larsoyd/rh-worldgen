package rhworld.mechanics;

import java.util.Optional;

public final class Replay {
    private Replay() {}

    // needs only the public key, the dice come from the openings the log already carries
    public static Optional<String> check(RideLog log) {
        WildGate gate = WildGate.forModulus(log.modulus());
        for (Ride r : log.rides()) {
            if (r.reported().colour() != r.colour()) return Optional.of("refused: reported realm has the wrong colour index=" + r.index());
            if (!gate.ride(r.before(), r.colour(), Dice.forOpening(r.opening(), r.index(), gate.size())).equals(r.after()))
                return Optional.of("refused: ride after differs from the replay index=" + r.index());
        }
        return Optional.empty();
    }
}
