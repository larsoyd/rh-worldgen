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
        var session = Session.current();
        if (session.isEmpty()) return GateHandler.NO_SESSION;
        Realm here = Realm.byDimension(player.level().dimension());
        if (here == null) return "refused: no sigil outside the four realms";
        var concord = PlayerState.concord(player);
        if (!concord.grant(here.mechanics())) return "refused: sigil of " + here.getSerializedName() + " already held";
        PlayerState.setConcord(player, concord);
        Log.line("sigil", "player", player.getStringUUID(), "realm", here.getSerializedName(), "held", concord.sigils().size());
        player.sendSystemMessage(Component.literal("Sigil of " + here.getSerializedName() + " claimed, "
                + concord.sigils().size() + " of 4"));
        if (concord.complete()) {
            advancement(player).ifPresent(a -> player.getAdvancements().award(a, "concord"));
            Log.line("concord", "player", player.getStringUUID(), "result", "complete");
        }
        return "claimed " + here.getSerializedName();
    }

    public static boolean awarded(ServerPlayer player) {
        return advancement(player).map(a -> player.getAdvancements().getOrStartProgress(a).isDone()).orElse(false);
    }

    private static java.util.Optional<AdvancementHolder> advancement(ServerPlayer player) {
        return java.util.Optional.ofNullable(player.level().getServer().getAdvancements().get(CONCORD.identifier()));
    }
}
