package rhworld.fabric;

import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import rhworld.mechanics.Colour;
import rhworld.mechanics.Ride;
import rhworld.mechanics.Season;

public final class RideCore {
    public static final String NO_SESSION = "refused: this world has no rhworld session";
    public static final String NO_KEY = "refused: no operator key on this server";

    public record Outcome(Optional<Ride> ride, Optional<String> refusal, Optional<Season.Player> state) {
        static Outcome refused(String reason) {
            return new Outcome(Optional.empty(), Optional.of(reason), Optional.empty());
        }
    }

    private RideCore() {}

    // rides a fresh copy and saves it, so a refused ride leaves the stored state as it was
    public static Outcome ride(Player p, Colour colour) {
        var out = attempt(p, colour);
        out.state().ifPresent(s -> {
            PlayerData.save(p, s);
            PlayerData.used(p);
        });
        return out;
    }

    static Outcome attempt(Player p, Colour colour) {
        var session = Session.current();
        if (session.isEmpty()) return Outcome.refused(NO_SESSION);
        if (session.get().season().isEmpty()) return Outcome.refused(NO_KEY);
        var season = session.get().season().get();
        try {
            var state = PlayerData.state(p, season);
            String opening = PlayerData.opening(p, state);
            if (opening.startsWith("refused:")) return Outcome.refused(opening);
            Ride ride = season.ride(state, colour, opening);
            return new Outcome(Optional.of(ride), Optional.empty(), Optional.of(state));
        } catch (IllegalArgumentException | IllegalStateException e) {
            String m = String.valueOf(e.getMessage());
            return Outcome.refused(m.startsWith("refused:") ? m : "refused: " + m);
        }
    }
}
