package rhworld.fabric;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class Lifecycle {
    private Lifecycle() {}

    public static void afterRespawn(ServerPlayer old, ServerPlayer fresh, boolean alive) {
        if (alive || Session.current().isEmpty()) return;
        PlayerState.setAttunement(fresh, java.math.BigInteger.ONE);
        if (!fresh.level().dimension().equals(Level.OVERWORLD)) GateHandler.travel(fresh, Realm.HEARTH);
        Log.line("death", "player", fresh.getStringUUID(), "attunement", 1, "dimension", fresh.level().dimension().identifier());
    }

    public static void join(ServerPlayer player) {
        var session = Session.current();
        if (session.isEmpty() || session.get().warden().isEmpty()) return;
        var realm = Realm.of(session.get().warden().get().realm(PlayerState.attunement(player)));
        if (player.level().dimension().equals(realm.dimension())) return;
        String moved = GateHandler.travel(player, realm);
        Log.line("join", "player", player.getStringUUID(), "realm", realm.getSerializedName(), "result", moved);
    }
}
