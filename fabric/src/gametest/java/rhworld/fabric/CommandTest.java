package rhworld.fabric;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

public final class CommandTest {
    static final List<String> SUBCOMMANDS = List.of("inspect", "contracts", "verify", "neighbours", "route ember",
            "resources", "audit", "reveal 12345");

    record Run(int result, List<String> lines) {}

    static Run run(GameTestHelper helper, String command) throws CommandSyntaxException {
        var lines = new ArrayList<String>();
        CommandSource sink = new CommandSource() {
            @Override public void sendSystemMessage(Component message) { lines.add(message.getString()); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        var server = helper.getLevel().getServer();
        var source = server.createCommandSourceStack().withSource(sink);
        int result = server.getCommands().getDispatcher().execute(command, source);
        return new Run(result, lines);
    }

    @GameTest
    public void everySubcommandRefusesUntilTheEngineIsWired(GameTestHelper helper) throws CommandSyntaxException {
        for (String sub : SUBCOMMANDS) {
            var r = run(helper, "rhworld " + sub);
            helper.assertValueEqual(r.result(), 0, sub + " result");
            helper.assertTrue(r.lines().size() == 1 && r.lines().get(0).contains(GateHandler.REFUSED_WORLD),
                    sub + " output " + r.lines());
        }
        helper.succeed();
    }

    @GameTest
    public void unknownRealmIsAParseError(GameTestHelper helper) {
        try {
            run(helper, "rhworld route void");
            helper.fail("route void was accepted");
        } catch (CommandSyntaxException expected) {
            helper.succeed();
        }
    }
}
