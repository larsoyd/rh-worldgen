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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import rhworld.mechanics.Colour;

final class LifeScenario {
    static final int PERSIST_RIDES = 30;

    private LifeScenario() {}

    static FakePlayer rider(MinecraftServer server, int seedByte) {
        var player = FakePlayer.get(server.overworld());
        byte[] seed = new byte[32];
        seed[0] = (byte) seedByte;
        PlayerState.setSeed(player, seed);
        return player;
    }

    static boolean ride(ServerPlayer player, Colour colour) {
        player.setPortalCooldown(0);
        return GateHandler.enter(player, colour).equals("ok");
    }

    static void concord(WorldTestHook.Ctx ctx) {
        var player = rider(ctx.server(), 7);
        var claimed = EnumSet.noneOf(Realm.class);
        int rides = 0;
        while (claimed.size() < 4 && rides < 200) {
            if (!ride(player, rides % 2 == 0 ? Colour.WARM : Colour.COLD)) break;
            rides++;
            String r = Sigils.claim(player);
            if (r.startsWith("claimed")) claimed.add(Realm.byDimension(player.level().dimension()));
        }
        var concord = PlayerState.concord(player);
        ctx.report().check(claimed.size() == 4 && concord.complete(), "concord_complete", "rides=" + rides + " sigils=" + concord.sigils());
        ctx.report().check(ctx.server().getAdvancements().get(Sigils.CONCORD.identifier()) != null, "concord_advancement_loaded",
                "fake players cannot hold advancements, a real player is covered by the client test");
        ctx.report().check(Sigils.claim(player).startsWith("refused:"), "sigil_once_per_realm", "second claim refused");

        BigInteger before = PlayerState.attunement(player);
        var reborn = FakePlayer.get(ctx.server().overworld(), new com.mojang.authlib.GameProfile(
                java.util.UUID.nameUUIDFromBytes("rhworld-reborn".getBytes()), "reborn"));
        var ember = ctx.server().getLevel(Realm.EMBER.dimension());
        reborn.teleportTo(ember, 0.5, 120, 0.5, java.util.Set.of(), 0f, 0f, true);
        ctx.report().check(reborn.level() == ember, "respawn_starts_in_a_realm", "dimension=" + reborn.level().dimension().identifier());
        reborn.restoreFrom(player, false);
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(player, reborn, false);
        ctx.report().check(PlayerState.attunement(reborn).equals(BigInteger.ONE), "death_resets_attunement",
                "before_bits=" + before.bitLength() + " after_bits=" + PlayerState.attunement(reborn).bitLength());
        ctx.report().check(PlayerState.seed(reborn) != null && PlayerState.concord(reborn).complete(), "death_keeps_dice_and_sigils", "");
        ctx.report().check(reborn.level().dimension().equals(Level.OVERWORLD), "death_respawns_in_hearth",
                "dimension=" + reborn.level().dimension().identifier());
    }

    static Path saved(MinecraftServer server, String name) {
        return server.getWorldPath(LevelResource.ROOT).resolve(name);
    }

    static void persist1(WorldTestHook.Ctx ctx) {
        var player = rider(ctx.server(), 99);
        int done = 0;
        for (int i = 0; i < PERSIST_RIDES; i++) if (ride(player, i % 2 == 0 ? Colour.WARM : Colour.COLD)) done++;
        ctx.report().check(done == PERSIST_RIDES, "persist_rides", "done=" + done);
        try {
            var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, ctx.server().registryAccess());
            player.saveWithoutId(out);
            NbtIo.writeCompressed(out.buildResult(), saved(ctx.server(), "rhworld-test-player.dat"));
            Files.writeString(saved(ctx.server(), "rhworld-test-expected.txt"), PlayerState.attunement(player) + " " + done + "\n");
            ctx.report().check(true, "persist_saved", "rides=" + done);
        } catch (IOException e) {
            ctx.report().fail("persist_saved", e.toString());
        }
    }

    static void persist2(WorldTestHook.Ctx ctx) {
        var s = Session.current().orElseThrow();
        try {
            var tag = NbtIo.readCompressed(saved(ctx.server(), "rhworld-test-player.dat"), NbtAccounter.unlimitedHeap());
            String[] expected = Files.readString(saved(ctx.server(), "rhworld-test-expected.txt")).trim().split(" ");
            var player = FakePlayer.get(ctx.server().overworld());
            player.load(TagValueInput.create(ProblemReporter.DISCARDING, ctx.server().registryAccess(), tag));
            BigInteger stored = PlayerState.attunement(player);
            int rides = PlayerState.rides(player, s.n()).rides().size();
            ctx.report().check(stored.toString().equals(expected[0]) && rides == Integer.parseInt(expected[1]),
                    "restart_keeps_attunement_and_log", "rides=" + rides + " x_bits=" + stored.bitLength());
            BigInteger x = BigInteger.ONE;
            byte[] seed = PlayerState.seed(player);
            for (int i = 0; i < rides; i++) {
                x = RideCore.ride(s, x, i % 2 == 0 ? Colour.WARM : Colour.COLD, seed, i).ride().orElseThrow().after();
            }
            ctx.report().check(x.equals(stored), "stored_x_equals_replay", "rides=" + rides);
        } catch (IOException | RuntimeException e) {
            ctx.report().fail("restart_keeps_attunement_and_log", e.toString());
        }
    }
}
