package rhworld.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import rhworld.engine.cert.Certificate;
import rhworld.engine.world.GmWorld;
import rhworld.mechanics.Audit;
import rhworld.mechanics.Colour;
import rhworld.mechanics.Custody;
import rhworld.mechanics.Replay;
import rhworld.mechanics.Season;

public final class RhCommands {
    static final DynamicCommandExceptionType UNKNOWN_REALM =
            new DynamicCommandExceptionType(name -> Component.literal("unknown realm " + name));
    static final double AUDIT_THRESHOLD = Math.pow(2, -20);

    private RhCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("rhworld");
        root.then(Commands.literal("inspect").executes(ctx -> run(ctx, "inspect", RhCommands::inspect)));
        root.then(Commands.literal("contracts").executes(ctx -> run(ctx, "contracts", RhCommands::contracts)));
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
        root.then(Commands.literal("log").executes(ctx -> run(ctx, "log", RhCommands::log)));
        root.then(Commands.literal("commit").then(Commands.argument("hex", StringArgumentType.word())
                .executes(ctx -> run(ctx, "commit", (src, s) -> one(PlayerData.commit(player(src), StringArgumentType.getString(ctx, "hex")))))));
        root.then(Commands.literal("open").then(Commands.argument("hex", StringArgumentType.word())
                .executes(ctx -> run(ctx, "open", (src, s) -> one(PlayerData.queue(player(src), StringArgumentType.getString(ctx, "hex")))))));
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
            lines = session.isEmpty() ? List.of(RideCore.NO_SESSION) : body.apply(ctx.getSource(), session.get());
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

    static Season season(Session s) {
        return s.season().orElseThrow(() -> new IllegalStateException(RideCore.NO_KEY));
    }

    static List<String> one(String line) {
        return List.of(line);
    }

    static List<String> inspect(CommandSourceStack src, Session s) {
        var p = player(src);
        var state = PlayerData.state(p, season(s));
        BigInteger x = state.attunement();
        return List.of("x=" + x.toString(16) + " x_bits=" + x.bitLength()
                + " public=" + Colour.ofJacobi(s.world().jacobi(x)).name().toLowerCase()
                + " dimension=" + p.level().dimension().identifier()
                + " rides=" + state.log().rides().size() + " sigils=" + state.concord().sigils().size()
                + " dice=" + state.log().custody().label());
    }

    static List<String> contracts(CommandSourceStack src, Session s) {
        var lines = new ArrayList<String>();
        lines.add("world_id=" + s.worldId() + " certificate=rhworld.cert/1");
        lines.addAll(Contracts.engine());
        Contracts.UNCONDITIONAL.forEach(c -> lines.add(c.line()));
        if (src.getEntity() instanceof ServerPlayer p && s.season().isPresent()
                && PlayerData.state(p, s.season().get()).log().custody() == Custody.SERVER) {
            lines.add("player=" + p.getScoreboardName() + " dice=server-held condition8=not_met, the server knows your future dice");
        }
        return lines;
    }

    @SuppressWarnings("unchecked")
    static List<String> verify(Session s) {
        var root = (java.util.Map<String, Object>) rhworld.engine.json.Json.parse(s.certificate());
        var rec = (java.util.Map<String, Object>) root.get("recomputed");
        var c1 = (java.util.Map<String, Object>) rec.get("c1");
        var c3 = (java.util.Map<String, Object>) rec.get("c3");
        var g2 = (java.util.Map<String, Object>) c1.get("g2");
        var checked = Certificate.verify(s.certificate(), Session.TELEMETRY);
        String verdict = checked instanceof rhworld.engine.contract.Verdict.Ok<?> ? "ok" : checked.toString();
        return List.of(
                "recomputed: c1 bits=" + c1.get("bits") + " min_bits=" + c1.get("min_bits") + " square=" + c1.get("square")
                        + " probable_prime=" + c1.get("probable_prime") + " trial_limit=" + c1.get("trial_limit")
                        + " primes_tested=" + c1.get("primes_tested") + " budget=" + c1.get("budget") + " g2_witness=" + g2.get("witness"),
                "recomputed: c3 bound=" + c3.get("bound") + " independent=" + c3.get("independent") + " primality_test=" + rec.get("primality_test"),
                "recomputed: certificate world_id=" + s.worldId() + " verify=" + verdict,
                "by-theorem: " + rhworld.engine.contract.C1.ID + " and " + rhworld.engine.contract.C3.ID
                        + " hold for this admitted n if GRH holds, see /rhworld contracts",
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
                .map(sh -> "shrine l=" + ShrineBlock.PRIMES[sh.index()] + " at=" + sh.origin().toShortString()
                        + " distance=" + (int) Math.sqrt(sh.origin().distSqr(p.blockPosition())) + " inert in this version")
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
        var log = PlayerData.state(player(src), season(s)).log();
        return List.of(Audit.of(log, AUDIT_THRESHOLD).line());
    }

    static List<String> log(CommandSourceStack src, Session s) {
        var log = PlayerData.state(player(src), season(s)).log();
        String replay = Replay.check(log).orElse("ok");
        return List.of("[log] rides=" + log.rides().size() + " dice=" + log.custody().label() + " replay=" + replay, log.toJson());
    }

    static List<String> reveal(CommandSourceStack src, Session s, String arg) {
        BigInteger p = strictDecimal(arg, s.world().n());
        if (p == null) return List.of("refused: p is not a decimal integer below n");
        var gm = GmWorld.reveal(s.world(), p, Optional.empty(), Session.TELEMETRY);
        if (!(gm instanceof rhworld.engine.contract.Verdict.Ok<GmWorld> ok)) return List.of(gm.toString());
        var season = s.season().orElseGet(() -> new Season(ok.value()));
        var lines = new ArrayList<String>();
        for (ServerPlayer player : src.getServer().getPlayerList().getPlayers()) {
            var log = PlayerData.state(player, season).log();
            var publicReplay = Replay.check(log);
            String hidden = publicReplay.isPresent() ? "skipped" : season.replay(log).stream()
                    .mapToObj(i -> "first_false_report=" + i).findFirst().orElse("all_reports_true");
            lines.add("player=" + player.getScoreboardName() + " rides=" + log.rides().size() + " dice=" + log.custody().label()
                    + " public_replay=" + publicReplay.orElse("ok") + " " + hidden);
        }
        lines.add("reveal accepted rank=" + ok.value().hiddenBasis().rank() + " factors=" + ok.value().factorsStatus()
                + " world_id=" + Certificate.of(ok.value()).worldId());
        return lines;
    }

    static BigInteger strictDecimal(String arg, BigInteger n) {
        if (arg.isEmpty() || arg.length() > n.toString().length() || arg.charAt(0) == '0'
                || !arg.chars().allMatch(c -> c >= '0' && c <= '9')) return null;
        BigInteger p = new BigInteger(arg);
        return p.compareTo(n) < 0 ? p : null;
    }
}
