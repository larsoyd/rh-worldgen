package rhworld.fabric;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import rhworld.mechanics.Colour;

final class CommandScenario {
    static final List<String> COMMANDS = List.of("inspect", "contracts", "verify", "neighbours", "route frost", "resources", "audit", "log");

    private CommandScenario() {}

    static void commands(WorldTestHook.Ctx ctx) {
        var player = FakePlayer.get(ctx.server().overworld());
        for (int i = 0; i < 6; i++) {
            player.setPortalCooldown(0);
            GateHandler.enter(player, i % 2 == 0 ? Colour.WARM : Colour.COLD);
        }
        for (String sub : COMMANDS) {
            var lines = new ArrayList<String>();
            CommandSource sink = new CommandSource() {
                @Override public void sendSystemMessage(Component message) { lines.add(message.getString()); }
                @Override public boolean acceptsSuccess() { return true; }
                @Override public boolean acceptsFailure() { return true; }
                @Override public boolean shouldInformAdmins() { return false; }
            };
            var source = ctx.server().createCommandSourceStack().withEntity(player).withSource(sink);
            int result;
            try {
                result = ctx.server().getCommands().getDispatcher().execute("rhworld " + sub, source);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                result = -1;
                lines.add(e.getMessage());
            }
            String text = String.join("\n", lines);
            text.lines().limit(4).forEach(l -> System.out.println("[rhworld-cmd] " + sub + " | " + (l.length() > 240 ? l.substring(0, 240) + "..." : l)));
            ctx.report().check(result == 1 && !text.startsWith("refused:"), "command_" + sub.split(" ")[0], "result=" + result);
        }
    }
}
