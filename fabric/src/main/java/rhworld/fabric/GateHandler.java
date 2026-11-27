package rhworld.fabric;

import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import rhworld.mechanics.Colour;

public final class GateHandler {
    public static final int COOLDOWN_TICKS = 40;

    private GateHandler() {}

    public static String enter(ServerPlayer player, Colour colour) {
        if (player.isOnPortalCooldown()) return "cooldown";
        player.setPortalCooldown(COOLDOWN_TICKS);
        long start = System.nanoTime();
        var out = RideCore.attempt(player, colour);
        if (out.refusal().isPresent()) return tell(player, colour, out.refusal().get());
        var ride = out.ride().get();
        long rideUs = (System.nanoTime() - start) / 1000;
        String moved = travel(player, Realm.of(ride.reported()));
        if (!moved.equals("ok")) return tell(player, colour, moved);
        PlayerData.save(player, out.state().get());
        PlayerData.used(player);
        Log.line("ride", "player", player.getStringUUID(), "index", ride.index(), "colour", colour, "realm", ride.reported(),
                "dice", out.state().get().log().custody().label(), "ride_us", rideUs, "total_us", (System.nanoTime() - start) / 1000);
        player.sendSystemMessage(Component.literal("The " + colour.name().toLowerCase() + " gate carries you to "
                + Realm.of(ride.reported()).getSerializedName()));
        return "ok";
    }

    public static String travel(ServerPlayer player, Realm realm) {
        var level = player.level().getServer().getLevel(realm.dimension());
        if (level == null) return "refused: realm dimension missing " + realm.getSerializedName();
        var hub = Hubs.origin(level);
        if (hub.isEmpty()) return "refused: no hub in " + realm.getSerializedName();
        var at = hub.get();
        player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 3.5, Set.of(), player.getYRot(), player.getXRot(), true);
        return player.level() == level ? "ok" : "refused: teleport failed to " + realm.getSerializedName();
    }

    private static String tell(ServerPlayer player, Colour colour, String reason) {
        Log.line("gate", "player", player.getStringUUID(), "colour", colour, "result", reason);
        player.sendSystemMessage(Component.literal("Wild Gate: " + reason));
        return reason;
    }
}
