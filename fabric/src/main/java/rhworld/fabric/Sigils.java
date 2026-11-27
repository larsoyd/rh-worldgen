package rhworld.fabric;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

public final class Sigils {
    public static final ResourceKey<net.minecraft.advancements.Advancement> CONCORD =
            ResourceKey.create(Registries.ADVANCEMENT, RhWorldMod.id("concord"));

    private Sigils() {}

    public static String claim(ServerPlayer player) {
        var season = Session.current().flatMap(Session::season);
        if (season.isEmpty()) return RideCore.NO_KEY;
        var state = PlayerData.state(player, season.get());
        var realm = Realm.of(season.get().realm(state));
        if (!player.level().dimension().equals(realm.dimension())) return "refused: this pedestal is not in your realm";
        if (!season.get().collect(state)) return "refused: sigil of " + realm.getSerializedName() + " already held";
        PlayerData.save(player, state);
        int held = state.concord().sigils().size();
        Log.line("sigil", "player", player.getStringUUID(), "realm", realm.getSerializedName(), "held", held);
        player.sendSystemMessage(Component.literal("Sigil of " + realm.getSerializedName() + " claimed, " + held + " of 4"));
        if (state.concord().complete()) {
            advancement(player).ifPresent(a -> player.getAdvancements().award(a, "concord"));
            Log.line("concord", "player", player.getStringUUID(), "result", "complete");
        }
        return "claimed " + realm.getSerializedName();
    }

    public static boolean awarded(ServerPlayer player) {
        return advancement(player).map(a -> player.getAdvancements().getOrStartProgress(a).isDone()).orElse(false);
    }

    private static java.util.Optional<AdvancementHolder> advancement(ServerPlayer player) {
        return java.util.Optional.ofNullable(player.level().getServer().getAdvancements().get(CONCORD.identifier()));
    }
}
