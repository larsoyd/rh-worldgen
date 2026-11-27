package rhworld.fabric;

import net.minecraft.server.level.ServerPlayer;

public final class Lifecycle {
    private Lifecycle() {}

    // listener order against the attachment copy is not fixed, so carry the state over before reading it
    public static void afterRespawn(ServerPlayer old, ServerPlayer fresh, boolean alive) {
        PlayerData.flush(old);
        for (var type : java.util.List.of(PlayerData.STATE, PlayerData.CHAIN, PlayerData.OPENING)) {
            String v = old.getAttached(type);
            if (v != null) fresh.setAttached(type, v);
        }
        keepInRealm(fresh, "respawn");
    }

    public static void join(ServerPlayer player) {
        keepInRealm(player, "join");
    }

    // the dimension always follows the realm of the stored attunement
    static void keepInRealm(ServerPlayer player, String why) {
        var season = Session.current().flatMap(Session::season);
        if (season.isEmpty()) return;
        var realm = Realm.of(season.get().realm(PlayerData.state(player, season.get())));
        if (player.level().dimension().equals(realm.dimension())) return;
        String moved = GateHandler.travel(player, realm);
        Log.line(why, "player", player.getStringUUID(), "realm", realm.getSerializedName(), "result", moved);
    }
}
