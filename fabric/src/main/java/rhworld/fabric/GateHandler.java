package rhworld.fabric;

import java.math.BigInteger;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import rhworld.mechanics.Colour;
import rhworld.mechanics.Ride;

public final class GateHandler {
    public static final String NO_SESSION = "refused: this world has no rhworld session";
    public static final int COOLDOWN_TICKS = 40;

    private GateHandler() {}

    public static String enter(ServerPlayer player, Colour colour) {
        if (player.isOnPortalCooldown()) return "cooldown";
        player.setPortalCooldown(COOLDOWN_TICKS);
        var session = Session.current();
        if (session.isEmpty()) return tell(player, colour, NO_SESSION);
        var s = session.get();
        var log = PlayerState.rides(player, s.n());
        BigInteger x = PlayerState.attunement(player);
        long start = System.nanoTime();
        var out = RideCore.ride(s, x, colour, PlayerState.seed(player), log.rides().size());
        if (out.refusal().isPresent()) return tell(player, colour, out.refusal().get());
        Ride ride = out.ride().get();
        long rideUs = (System.nanoTime() - start) / 1000;
        String moved = travel(player, Realm.of(ride.reported()));
        if (!moved.equals("ok")) return tell(player, colour, moved);
        log.add(ride);
        PlayerState.setRides(player, log);
        PlayerState.setAttunement(player, ride.after());
        Log.line("ride", "player", player.getStringUUID(), "index", ride.index(), "colour", colour,
                "realm", ride.reported(), "x_bits", ride.after().bitLength(), "ride_us", rideUs, "total_us", (System.nanoTime() - start) / 1000);
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
