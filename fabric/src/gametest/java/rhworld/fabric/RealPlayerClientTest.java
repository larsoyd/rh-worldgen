package rhworld.fabric;

import java.util.EnumSet;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.level.ServerPlayer;
import rhworld.mechanics.Colour;

public final class RealPlayerClientTest implements FabricClientGameTest {
    static void check(boolean ok, String what, String detail) {
        System.out.printf("[rhworld-client] check=%s ok=%s %s%n", what, ok, detail);
        if (!ok) throw new AssertionError(what + " " + detail);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        var builder = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(state -> {
            var entry = state.getNormalPresetList().stream()
                    .filter(e -> e.preset().is(RhWorldMod.id("wild_rigged")))
                    .findFirst();
            check(entry.isPresent(), "preset_listed", "presets=" + state.getNormalPresetList().size());
            state.setWorldType(entry.get());
            state.setSeed("12345");
        });
        try (var sp = builder.create()) {
            sp.getConnection().waitForChunksRender();
            String result = sp.getServer().computeOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                check(Session.current().flatMap(Session::warden).isPresent(), "session_with_key", "");
                byte[] seed = new byte[32];
                seed[0] = 7;
                PlayerState.setSeed(player, seed);
                var claimed = EnumSet.noneOf(Realm.class);
                int rides = 0;
                while (claimed.size() < 4 && rides < 200) {
                    player.setPortalCooldown(0);
                    String r = GateHandler.enter(player, rides % 2 == 0 ? Colour.WARM : Colour.COLD);
                    check(r.equals("ok"), "ride", "index=" + rides + " result=" + r);
                    rides++;
                    if (Sigils.claim(player).startsWith("claimed")) claimed.add(Realm.byDimension(player.level().dimension()));
                }
                check(claimed.size() == 4, "all_sigils", "rides=" + rides);
                check(Sigils.awarded(player), "concord_advancement_real_player", "awarded=" + Sigils.awarded(player));
                GateHandler.travel(player, Realm.EMBER);
                check(!player.isInWater() && player.level().getBlockState(player.blockPosition().below()).is(RhBlocks.HUB_STONE),
                        "standing_dry_on_the_pad", "in_water=" + player.isInWater() + " y=" + player.getBlockY());
                return player.level().dimension().identifier().toString();
            });
            check(result.equals("rhworld:ember"), "at_ember_hub", "dimension=" + result);
            sp.getConnection().waitForChunksRender();
            var shot = context.takeScreenshot("ember-hub");
            check(shot != null && shot.toFile().length() > 0, "screenshot", "path=" + shot);
            sp.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(player.level(), 0.5, player.getY(), 0.5, java.util.Set.of(), 90f, 0f, true);
            });
            context.waitTicks(220);
            sp.getConnection().waitForChunksRender();
            context.takeScreenshot("ember-warm-gate");
        }
    }
}
