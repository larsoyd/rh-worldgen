package rhworld.fabric;

import java.util.EnumSet;
import net.fabricmc.fabric.api.entity.FakePlayer;
import rhworld.mechanics.Colour;
import rhworld.mechanics.Custody;
import rhworld.mechanics.Replay;

final class RideScenario {
    static final int RIDES = 400;

    private RideScenario() {}

    static void rides(WorldTestHook.Ctx ctx) {
        var session = Session.current();
        ctx.report().check(session.flatMap(Session::season).isPresent(), "session_with_key", "present=" + session.isPresent());
        if (session.flatMap(Session::season).isEmpty()) return;
        var s = session.get();
        var season = s.season().get();
        var player = FakePlayer.get(ctx.server().overworld());
        var reached = EnumSet.noneOf(rhworld.mechanics.Realm.class);
        int broken = 0, colourBroken = 0, refused = 0;
        long start = System.nanoTime();
        for (int i = 0; i < RIDES; i++) {
            Colour colour = i % 2 == 0 ? Colour.WARM : Colour.COLD;
            player.setPortalCooldown(0);
            String result = GateHandler.enter(player, colour);
            if (!result.equals("ok")) {
                refused++;
                if (refused == 1) ctx.report().fail("ride_refused", "ride=" + i + " result=" + result);
                continue;
            }
            var state = PlayerData.state(player, season);
            var realm = season.realm(state);
            reached.add(realm);
            if (!player.level().dimension().equals(Realm.of(realm).dimension())) broken++;
            if (Colour.ofJacobi(s.world().jacobi(state.attunement())) != colour) colourBroken++;
        }
        long micros = (System.nanoTime() - start) / 1000 / RIDES;
        var log = PlayerData.state(player, season).log();
        ctx.report().check(refused == 0, "rides_done", "rides=" + RIDES + " refused=" + refused + " us_per_ride=" + micros);
        ctx.report().check(broken == 0, "i1_dimension_is_realm_of_x", "broken=" + broken);
        ctx.report().check(colourBroken == 0, "colour_law", "broken=" + colourBroken);
        ctx.report().check(reached.size() == 4, "all_realms_reached", "reached=" + reached);
        ctx.report().check(log.custody() == Custody.SERVER, "vanilla_dice_marked_server_held", "dice=" + log.custody().label());
        var replay = Replay.check(log);
        ctx.report().check(log.rides().size() == RIDES && replay.isEmpty(), "log_replays", "logged=" + log.rides().size() + " replay=" + replay.orElse("ok"));
    }
}
