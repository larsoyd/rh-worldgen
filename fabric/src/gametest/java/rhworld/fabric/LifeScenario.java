package rhworld.fabric;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import rhworld.mechanics.Colour;

final class LifeScenario {
    static final int PERSIST_RIDES = 30;

    private LifeScenario() {}

    static FakePlayer rider(MinecraftServer server) {
        return FakePlayer.get(server.overworld());
    }

    static rhworld.mechanics.Season season() {
        return Session.current().flatMap(Session::season).orElseThrow();
    }

    static boolean ride(ServerPlayer player, Colour colour) {
        player.setPortalCooldown(0);
        return GateHandler.enter(player, colour).equals("ok");
    }

    static void concord(WorldTestHook.Ctx ctx) {
        var player = rider(ctx.server());
        var claimed = EnumSet.noneOf(Realm.class);
        int rides = 0;
        while (claimed.size() < 4 && rides < 200) {
            if (!ride(player, rides % 2 == 0 ? Colour.WARM : Colour.COLD)) break;
            rides++;
            String r = Sigils.claim(player);
            if (r.startsWith("claimed")) claimed.add(Realm.byDimension(player.level().dimension()));
        }
        var concord = PlayerData.state(player, season()).concord();
        ctx.report().check(claimed.size() == 4 && concord.complete(), "concord_complete", "rides=" + rides + " sigils=" + concord.sigils());
        ctx.report().check(ctx.server().getAdvancements().get(Sigils.CONCORD.identifier()) != null, "concord_advancement_loaded",
                "fake players cannot hold advancements, a real player is covered by the client test");
        ctx.report().check(Sigils.claim(player).startsWith("refused:"), "sigil_once_per_realm", "second claim refused");

        for (int extra = 0; extra < 50 && season().realm(PlayerData.state(player, season())) == rhworld.mechanics.Realm.HEARTH; extra++) {
            ride(player, extra % 2 == 0 ? Colour.WARM : Colour.COLD);
        }
        var state = PlayerData.state(player, season());
        BigInteger before = state.attunement();
        var home = Realm.of(season().realm(state));
        var elsewhere = home == Realm.EMBER ? Realm.FROST : Realm.EMBER;
        var reborn = FakePlayer.get(ctx.server().overworld(), new com.mojang.authlib.GameProfile(
                java.util.UUID.nameUUIDFromBytes("rhworld-reborn".getBytes()), "reborn"));
        reborn.teleportTo(ctx.server().getLevel(elsewhere.dimension()), 0.5, 120, 0.5, java.util.Set.of(), 0f, 0f, true);
        ctx.report().check(home != Realm.HEARTH && reborn.level().dimension().equals(elsewhere.dimension()), "respawn_starts_outside_the_realm",
                "home=" + home.getSerializedName() + " start=" + reborn.level().dimension().identifier());
        reborn.restoreFrom(player, false);
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(player, reborn, false);
        var after = PlayerData.state(reborn, season());
        ctx.report().check(after.attunement().equals(before) && after.log().rides().size() == state.log().rides().size(),
                "death_keeps_attunement_and_log", "x_kept=" + after.attunement().equals(before) + " rides=" + after.log().rides().size());
        ctx.report().check(after.concord().complete(), "death_keeps_sigils", "");
        ctx.report().check(reborn.level().dimension().equals(home.dimension()), "respawn_at_hub_of_own_realm",
                "home=" + home.getSerializedName() + " dimension=" + reborn.level().dimension().identifier());
    }

    static Path saved(MinecraftServer server, String name) {
        return server.getWorldPath(LevelResource.ROOT).resolve(name);
    }

    static void persist1(WorldTestHook.Ctx ctx) {
        var player = rider(ctx.server());
        int done = 0;
        for (int i = 0; i < PERSIST_RIDES; i++) if (ride(player, i % 2 == 0 ? Colour.WARM : Colour.COLD)) done++;
        ctx.report().check(done == PERSIST_RIDES, "persist_rides", "done=" + done);
        try {
            var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, ctx.server().registryAccess());
            player.saveWithoutId(out);
            NbtIo.writeCompressed(out.buildResult(), saved(ctx.server(), "rhworld-test-player.dat"));
            Files.writeString(saved(ctx.server(), "rhworld-test-expected.txt"), PlayerData.state(player, season()).attunement() + " " + done + "\n");
            ctx.report().check(true, "persist_saved", "rides=" + done);
        } catch (IOException e) {
            ctx.report().fail("persist_saved", e.toString());
        }
    }

    static void persist2(WorldTestHook.Ctx ctx) {
        try {
            var tag = NbtIo.readCompressed(saved(ctx.server(), "rhworld-test-player.dat"), NbtAccounter.unlimitedHeap());
            String[] expected = Files.readString(saved(ctx.server(), "rhworld-test-expected.txt")).trim().split(" ");
            var player = FakePlayer.get(ctx.server().overworld());
            player.load(TagValueInput.create(ProblemReporter.DISCARDING, ctx.server().registryAccess(), tag));
            var state = PlayerData.state(player, season());
            int rides = state.log().rides().size();
            ctx.report().check(state.attunement().toString().equals(expected[0]) && rides == Integer.parseInt(expected[1]),
                    "restart_keeps_attunement_and_log", "rides=" + rides + " x_bits=" + state.attunement().bitLength());
            var replay = rhworld.mechanics.Replay.check(state.log());
            ctx.report().check(replay.isEmpty() && state.log().rides().getLast().after().equals(state.attunement()),
                    "stored_x_equals_replay", "rides=" + rides + " replay=" + replay.orElse("ok"));
        } catch (IOException | RuntimeException e) {
            ctx.report().fail("restart_keeps_attunement_and_log", e.toString());
        }
    }
}
