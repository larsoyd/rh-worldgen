package rhworld.fabric;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class GateHandler {
    public static final String REFUSED = "refused: engine gate api not available";
    public static final String REFUSED_WORLD = "refused: engine world api not available";

    private GateHandler() {}

    public static String enter(ServerPlayer player, GateBlock.Colour colour) {
        long tick = player.level().getGameTime();
        if (player.isOnPortalCooldown()) return "cooldown";
        player.setPortalCooldown(40);
        Log.line("gate", "player", player.getStringUUID(), "colour", colour, "tick", tick, "result", REFUSED);
        player.sendSystemMessage(Component.literal("Wild Gate: " + REFUSED));
        return REFUSED;
    }
}
