package rhworld.mechanics;

import java.util.Optional;
import rhworld.engine.contract.Verdict;
import rhworld.engine.telemetry.Telemetry;
import rhworld.engine.world.PublicWorld;

public final class Replay {
    private Replay() {}

    // needs only the public key, the dice come from the openings the log already carries
    public static Optional<String> check(RideLog log) {
        WildGate gate;
        switch (PublicWorld.open(log.modulus(), Telemetry.NONE)) {
            case Verdict.Ok<PublicWorld> ok -> gate = WildGate.of(ok.value());
            case Verdict.Refused<PublicWorld> r -> {
                return Optional.of(r.toString());
            }
        }
        for (Ride r : log.rides()) {
            if (r.reported().colour() != r.colour()) return Optional.of("refused: reported realm has the wrong colour index=" + r.index());
            if (!gate.ride(r.before(), r.colour(), Dice.forOpening(r.opening(), r.index(), gate.size())).equals(r.after()))
                return Optional.of("refused: ride after differs from the replay index=" + r.index());
        }
        return Optional.empty();
    }
}
