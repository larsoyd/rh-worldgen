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

    static Run tryRun(GameTestHelper helper, String command) {
        try {
            return run(helper, command);
        } catch (CommandSyntaxException e) {
            return new Run(0, List.of("refused: parse " + e.getMessage()));
        }
    }

    static String all(Run r) {
        return String.join("\n", r.lines());
    }

    @GameTest
    public void commandsRefuseWithoutASession(GameTestHelper helper) throws CommandSyntaxException {
        Session.set(null);
        for (String sub : List.of("inspect", "contracts", "verify", "neighbours", "route ember", "resources", "audit", "log", "reveal 3")) {
            var r = run(helper, "rhworld " + sub);
            helper.assertValueEqual(r.result(), 0, sub + " result");
            helper.assertTrue(all(r).contains(RideCore.NO_SESSION), sub + " output " + r.lines());
        }
        helper.succeed();
    }

    @GameTest
    public void contractsLabelTheShrinesUnconditional(GameTestHelper helper) throws CommandSyntaxException {
        Session.set(SeasonAdapterTest.rigged(false));
        var out = all(run(helper, "rhworld contracts"));
        Session.set(null);
        helper.assertTrue(out.contains("id=SHRINE label=unconditional grh=no kind=adapter, not an engine contract"), "shrine line missing: " + out);
        helper.assertTrue(out.contains("id=HUBS ") && out.contains("id=DICE "), "hub and dice items missing: " + out);
        helper.assertTrue(out.contains("gate guarantee basis=RHW-C3 v1 statement=\"colour as asked, hidden realm exactly uniform given the colour, for every admissible key\""),
                "gate guarantee missing: " + out);
        helper.assertTrue(out.contains("contract=RHW-C1 v1") && out.contains("contract=RHW-C3 v1"), "engine contracts missing: " + out);
        helper.assertTrue(out.contains("label=LEAN ref=LFunction_ne_zero_of_half_lt_re"), "lean chain missing: " + out);
        helper.assertTrue(out.contains("checked_by=recomputed.c1.trial_limit"), "hypotheses missing: " + out);
        helper.assertFalse(out.contains("id=WG-1") || out.contains("id=G2 "), "adapter invented contract ids: " + out);
        helper.assertFalse(out.lines().anyMatch(l -> l.contains("id=SHRINE") && l.contains("grh=yes")), "shrine claims grh: " + out);
        helper.succeed();
    }

    @GameTest
    public void verifySeparatesRecomputedFromTheorem(GameTestHelper helper) throws CommandSyntaxException {
        var s = SeasonAdapterTest.rigged(false);
        Session.set(s);
        var out = all(run(helper, "rhworld verify"));
        Session.set(null);
        helper.assertTrue(out.contains("recomputed: c1 bits=1024 min_bits=1024 square=false probable_prime=false trial_limit=504100"), "c1: " + out);
        helper.assertTrue(out.contains("budget=503791 g2_witness=349"), "budget and witness: " + out);
        helper.assertTrue(out.contains("recomputed: certificate world_id=" + s.worldId() + " verify=ok, the engine rechecking its own certificate from n"), "certificate: " + out);
        helper.assertTrue(out.contains("independent: rhworld verify " ) && out.contains("verify.certverify.verify"), "independent check: " + out);
        try {
            var file = helper.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("rhworld").resolve("world-" + s.worldId() + ".cert.json");
            helper.assertValueEqual(java.nio.file.Files.readString(file), s.certificate(), "exported public certificate");
        } catch (java.io.IOException e) {
            helper.fail("no exported certificate " + e);
        }
        helper.assertTrue(out.contains("by-theorem: G1 unchecked until reveal"), "g1: " + out);
        helper.assertFalse(out.contains("G1 verified"), "g1 claimed: " + out);
        helper.succeed();
    }

    @GameTest
    public void revealRefusesEveryMalformedFactor(GameTestHelper helper) {
        var s = SeasonAdapterTest.rigged(false);
        Session.set(s);
        var key = SeasonAdapterTest.riggedKey();
        var n = s.world().n();
        var p = key.p().min(key.q());
        var two = java.math.BigInteger.TWO;
        var bad = List.of("0", "1", n.subtract(java.math.BigInteger.ONE).toString(), n.toString(), n.add(java.math.BigInteger.ONE).toString(),
                "-1", "007", "+5", "0x10", "abc", "2147483647", "-2147483648", "9223372036854775807", "-9223372036854775808",
                "1".repeat(100000), p.multiply(two).toString(), p.multiply(p).toString(), " " + p, p + " ",
                p.toString().substring(0, 20) + " " + p.toString().substring(20));
        for (String arg : bad) {
            String out = all(tryRun(helper, "rhworld reveal " + arg));
            String shown = arg.substring(0, Math.min(20, arg.length()));
            helper.assertTrue(out.startsWith("refused: "), "reveal " + shown + " gave " + out);
            helper.assertFalse(out.contains(n.toString()) || out.contains(key.p().toString()) || out.contains(key.q().toString())
                    || out.length() > 300, "reveal echoed too much for " + shown);
        }
        helper.assertValueEqual(all(tryRun(helper, "rhworld reveal " + p.multiply(two))), "refused: revealed factor does not divide n",
                "engine string for a non divisor");
        helper.assertValueEqual(all(tryRun(helper, "rhworld reveal " + n.add(java.math.BigInteger.ONE))), "refused: revealed factor does not divide n",
                "engine string for p above n");
        Session.set(null);
        helper.succeed();
    }

    @GameTest
    public void revealOfTheLargerFactorIsCanonicalised(GameTestHelper helper) {
        var s = SeasonAdapterTest.rigged(false);
        Session.set(s);
        var key = SeasonAdapterTest.riggedKey();
        String out = all(tryRun(helper, "rhworld reveal " + key.p().max(key.q())));
        Session.set(null);
        helper.assertTrue(out.contains("reveal accepted") && out.contains("world_id=" + s.worldId()), "larger factor gave " + out);
        helper.assertFalse(out.contains(key.p().toString()) || out.contains(key.q().toString()), "factor echoed");
        helper.succeed();
    }

    @GameTest
    public void revealAcceptsTheTrueFactorAndReportsFullRank(GameTestHelper helper) throws CommandSyntaxException {
        var s = SeasonAdapterTest.rigged(true);
        Session.set(s);
        var p = SeasonAdapterTest.riggedKey().p().min(SeasonAdapterTest.riggedKey().q());
        var out = all(run(helper, "rhworld reveal " + p));
        Session.set(null);
        helper.assertTrue(out.contains("reveal accepted rank=2"), "reveal gave " + out);
        helper.assertTrue(out.contains("world_id=" + s.worldId()), "reveal world id: " + out);
        var file = helper.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("rhworld").resolve("revealed-" + s.worldId() + ".cert.json");
        helper.assertTrue(java.nio.file.Files.exists(file) && out.contains("certificate=" + file.getFileName()), "revealed certificate file: " + out);
        helper.assertFalse(out.contains(SeasonAdapterTest.riggedKey().q().toString()), "reveal printed the cofactor");
        helper.succeed();
    }

    @GameTest
    public void commitAndOpenAcceptOnly64LowerHexDigits(GameTestHelper helper) throws CommandSyntaxException {
        Session.set(SeasonAdapterTest.rigged(true));
        for (String sub : List.of("commit", "open")) {
            for (String arg : List.of("00", "g".repeat(64), "0".repeat(63), "0".repeat(65), "A".repeat(64))) {
                var out = all(run(helper, "rhworld " + sub + " " + arg));
                helper.assertTrue(out.startsWith("refused: "), sub + " " + arg + " gave " + out);
            }
        }
        Session.set(null);
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
