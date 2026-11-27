package rhworld.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Arrays;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class RhCommands {
    static final DynamicCommandExceptionType UNKNOWN_REALM =
            new DynamicCommandExceptionType(name -> Component.literal("unknown realm " + name));

    private RhCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("rhworld");
        for (String name : new String[] {"inspect", "contracts", "verify", "neighbours", "resources", "audit"}) {
            root.then(Commands.literal(name).executes(ctx -> refuse(ctx, name)));
        }
        root.then(Commands.literal("route").then(Commands.argument("realm", StringArgumentType.word())
                .suggests((ctx, b) -> {
                    Arrays.stream(Realm.values()).forEach(r -> b.suggest(r.getSerializedName()));
                    return b.buildFuture();
                })
                .executes(ctx -> {
                    realm(StringArgumentType.getString(ctx, "realm"));
                    return refuse(ctx, "route");
                })));
        root.then(Commands.literal("reveal").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("p", StringArgumentType.word()).executes(ctx -> refuse(ctx, "reveal"))));
        dispatcher.register(root);
    }

    static Realm realm(String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        for (Realm r : Realm.values()) if (r.getSerializedName().equals(name)) return r;
        throw UNKNOWN_REALM.create(name);
    }

    static int refuse(CommandContext<CommandSourceStack> ctx, String name) {
        Log.line("command", "name", name, "result", GateHandler.REFUSED_WORLD);
        ctx.getSource().sendFailure(Component.literal(GateHandler.REFUSED_WORLD));
        return 0;
    }
}
