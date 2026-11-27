package rhworld.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import rhworld.engine.arith.Arith;
import rhworld.engine.arith.Budget;
import rhworld.mechanics.Audit;
import rhworld.mechanics.Colour;
import rhworld.mechanics.Warden;

public final class RhCommands {
    static final DynamicCommandExceptionType UNKNOWN_REALM =
            new DynamicCommandExceptionType(name -> Component.literal("unknown realm " + name));
    static final double AUDIT_THRESHOLD = Math.pow(2, -20);

    private RhCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("rhworld");
        root.then(Commands.literal("inspect").executes(ctx -> run(ctx, "inspect", RhCommands::inspect)));
        root.then(Commands.literal("contracts").executes(ctx -> run(ctx, "contracts", (src, s) -> contracts())));
        root.then(Commands.literal("verify").executes(ctx -> run(ctx, "verify", (src, s) -> verify(s))));
        root.then(Commands.literal("neighbours").executes(ctx -> run(ctx, "neighbours", RhCommands::neighbours)));
        root.then(Commands.literal("resources").executes(ctx -> run(ctx, "resources", RhCommands::resources)));
        root.then(Commands.literal("audit").executes(ctx -> run(ctx, "audit", RhCommands::audit)));
        root.then(Commands.literal("route").then(Commands.argument("realm", StringArgumentType.word())
                .suggests((ctx, b) -> {
                    Arrays.stream(Realm.values()).forEach(r -> b.suggest(r.getSerializedName()));
                    return b.buildFuture();
                })
                .executes(ctx -> {
                    Realm target = realm(StringArgumentType.getString(ctx, "realm"));
                    return run(ctx, "route", (src, s) -> route(src, target));
                })));
        root.then(Commands.literal("dice").then(Commands.argument("hex", StringArgumentType.word())
                .executes(ctx -> run(ctx, "dice", (src, s) -> dice(src, StringArgumentType.getString(ctx, "hex"))))));
        root.then(Commands.literal("reveal").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("p", StringArgumentType.word())
                        .executes(ctx -> run(ctx, "reveal", (src, s) -> reveal(src, s, StringArgumentType.getString(ctx, "p"))))));
        dispatcher.register(root);
    }

    static Realm realm(String name) throws CommandSyntaxException {
        for (Realm r : Realm.values()) if (r.getSerializedName().equals(name)) return r;
        throw UNKNOWN_REALM.create(name);
    }

    static int run(CommandContext<CommandSourceStack> ctx, String name, BiFunction<CommandSourceStack, Session, List<String>> body) {
        var session = Session.current();
        List<String> lines;
        try {
            lines = session.isEmpty() ? List.of(GateHandler.NO_SESSION) : body.apply(ctx.getSource(), session.get());
        } catch (IllegalArgumentException | IllegalStateException e) {
            String m = String.valueOf(e.getMessage());
            lines = List.of(m.startsWith("refused:") ? m : "refused: " + m);
        }
        boolean refused = lines.size() == 1 && lines.get(0).startsWith("refused:");
        Log.line("command", "name", name, "lines", lines.size(), "result", refused ? lines.get(0) : "ok");
        var text = Component.literal(String.join("\n", lines));
        if (refused) ctx.getSource().sendFailure(text);
        else ctx.getSource().sendSuccess(() -> text, false);
        return refused ? 0 : 1;
    }

    static ServerPlayer player(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer p)) throw new IllegalStateException("refused: only a player has an attunement");
        return p;
    }

    static List<String> inspect(CommandSourceStack src, Session s) {
        var p = player(src);
        BigInteger x = PlayerState.attunement(p);
        var log = PlayerState.rides(p, s.n());
        return List.of("x=" + x.toString(16) + " x_bits=" + x.bitLength()
                + " public=" + Colour.ofJacobi(Arith.jacobi(x, s.n())).name().toLowerCase()
                + " dimension=" + p.level().dimension().identifier()
                + " rides=" + log.rides().size() + " sigils=" + PlayerState.concord(p).sigils().size());
    }

    static List<String> contracts() {
        return Contracts.ALL.stream().map(Contracts.Contract::line).toList();
    }

    static List<String> verify(Session s) {
        int k = s.n().bitLength();
        long budget = Budget.forBits(k);
        var small = Arith.leastFactor(s.n(), budget);
        return List.of(
                "recomputed: bits=" + k + " budget=" + budget + " gate_primes=" + s.gate().size() + " witness=" + s.gate().witness(),
                "recomputed: odd=" + yes(s.n().testBit(0)) + " square=" + yes(Arith.isPerfectSquare(s.n()))
                        + " probable_prime=" + yes(Arith.isProbablePrime(s.n())) + " small_factor=" + (small.isPresent() ? "found" : "none"),
                "by-theorem: G2 bound w <= B holds for every n passing the checks, here observed w=" + s.gate().witness(),
                "by-theorem: G1 unchecked until reveal");
    }

    static List<String> neighbours(CommandSourceStack src, Session s) {
        var p = player(src);
        var level = (ServerLevel) p.level();
        var hub = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getValue(RhWorldMod.id("hub"));
        var start = hub == null ? null : level.getChunk(0, 0).getStartForStructure(hub);
        if (start == null || !start.isValid()) return List.of("refused: no hub in this dimension");
        return start.getPieces().stream()
                .filter(piece -> piece instanceof HubPieces.Shrine)
                .map(piece -> (HubPieces.Shrine) piece)
                .filter(sh -> sh.origin().distSqr(p.blockPosition()) <= 64 * 64)
                .map(sh -> "shrine l=" + ShrineBlock.PRIMES[sh.index] + " at=" + sh.origin().toShortString()
                        + " distance=" + (int) Math.sqrt(sh.origin().distSqr(p.blockPosition())) + " label=unconditional")
                .toList();
    }

    static List<String> route(CommandSourceStack src, Realm target) {
        var p = player(src);
        if (p.level().dimension().equals(target.dimension())) return List.of("route realm=" + target.getSerializedName() + " you are there");
        String colour = target.mechanics().colour().name().toLowerCase();
        return List.of("route realm=" + target.getSerializedName() + " ride the " + colour
                + " Wild Gate, each ride lands there with probability 1/2");
    }

    static List<String> resources(CommandSourceStack src, Session s) {
        var server = src.getServer();
        return Arrays.stream(Realm.values()).map(r -> {
            var level = server.getLevel(r.dimension());
            var hub = level == null ? java.util.Optional.<net.minecraft.core.BlockPos>empty() : Hubs.origin(level);
            return hub.map(h -> "realm=" + r.getSerializedName() + " hub=" + h.toShortString() + " sigil=" + h.offset(0, 0, HubPieces.GATE_X).toShortString())
                    .orElse("realm=" + r.getSerializedName() + " hub=missing");
        }).toList();
    }

    static List<String> audit(CommandSourceStack src, Session s) {
        var v = Audit.of(PlayerState.rides(player(src), s.n()), AUDIT_THRESHOLD);
        return List.of(v.line());
    }

    static List<String> dice(CommandSourceStack src, String hex) {
        if (hex.length() != 64 || !hex.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
            return List.of("refused: dice seed must be exactly 64 hex digits");
        }
        PlayerState.setSeed(player(src), HexFormat.of().parseHex(hex));
        return List.of("dice seed set, rides from now on use it");
    }

    static List<String> reveal(CommandSourceStack src, Session s, String arg) {
        BigInteger p = strictFactor(arg, s.n());
        if (p == null) return List.of("refused: p is not a nontrivial factor of n");
        Warden warden = new Warden(p, s.n().divide(p));
        var lines = new java.util.ArrayList<String>();
        for (ServerPlayer player : src.getServer().getPlayerList().getPlayers()) {
            var log = PlayerState.rides(player, s.n());
            var first = Audit.replay(warden, log);
            lines.add("player=" + player.getScoreboardName() + " rides=" + log.rides().size()
                    + (first.isPresent() ? " first_false_report=" + first.getAsLong() : " all_reports_true"));
        }
        lines.add("reveal accepted rank=" + warden.rank(s.gate().primes()));
        return lines;
    }

    static BigInteger strictFactor(String arg, BigInteger n) {
        int digits = n.toString().length();
        if (arg.isEmpty() || arg.length() > digits || arg.charAt(0) == '0' || !arg.chars().allMatch(c -> c >= '0' && c <= '9')) return null;
        BigInteger p = new BigInteger(arg);
        if (p.compareTo(BigInteger.ONE) <= 0 || p.compareTo(n) >= 0 || n.mod(p).signum() != 0) return null;
        return p;
    }

    private static String yes(boolean b) {
        return b ? "yes" : "no";
    }
}
