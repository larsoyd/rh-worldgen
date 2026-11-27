package rhworld.fabric;

import java.math.BigInteger;
import java.util.EnumSet;
import net.fabricmc.fabric.api.entity.FakePlayer;
import rhworld.engine.arith.Arith;
import rhworld.mechanics.Colour;

final class RideScenario {
    static final int RIDES = 400;

    private RideScenario() {}

    static void rides(WorldTestHook.Ctx ctx) {
        var session = Session.current();
        ctx.report().check(session.isPresent() && session.get().warden().isPresent(), "session_with_key", "present=" + session.isPresent());
        if (session.isEmpty() || session.get().warden().isEmpty()) return;
        var s = session.get();
        var player = FakePlayer.get(ctx.server().overworld());
        byte[] seed = new byte[32];
        seed[0] = 42;
        PlayerState.setSeed(player, seed);
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
            BigInteger x = PlayerState.attunement(player);
            var realm = s.warden().get().realm(x);
            reached.add(realm);
            if (!player.level().dimension().equals(Realm.of(realm).dimension())) broken++;
            if (Colour.ofJacobi(Arith.jacobi(x, s.n())) != colour) colourBroken++;
        }
        long micros = (System.nanoTime() - start) / 1000 / RIDES;
        ctx.report().check(refused == 0, "rides_done", "rides=" + RIDES + " refused=" + refused + " us_per_ride=" + micros);
        ctx.report().check(broken == 0, "i1_dimension_is_realm_of_x", "broken=" + broken);
        ctx.report().check(colourBroken == 0, "colour_law", "broken=" + colourBroken);
        ctx.report().check(reached.size() == 4, "all_realms_reached", "reached=" + reached);
        var log = PlayerState.rides(player, s.n());
        ctx.report().check(log.rides().size() == RIDES - refused && log.rides().getLast().after().equals(PlayerState.attunement(player)),
                "ride_log_matches", "logged=" + log.rides().size());
    }
}
