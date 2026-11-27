package rhworld.fabric;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import rhworld.mechanics.Colour;
import rhworld.mechanics.Custody;
import rhworld.mechanics.Realm;
import rhworld.mechanics.SeedChain;

public final class SeasonAdapterTest {
    record Key(BigInteger n, BigInteger p, BigInteger q) {}

    static Key riggedKey() {
        try (var in = SeasonAdapterTest.class.getResourceAsStream("/rhworld-test/rigged-key.json")) {
            var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            var p = new BigInteger(json.get("p").getAsString());
            var q = new BigInteger(json.get("q").getAsString());
            return new Key(p.multiply(q), p, q);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static Session rigged(boolean withKey) {
        var k = riggedKey();
        var v = Session.open(k.n(), withKey ? java.util.Optional.of(new BigInteger[] {k.p(), k.q()}) : java.util.Optional.empty());
        if (v instanceof rhworld.engine.contract.Verdict.Ok<Session> ok) return ok.value();
        throw new AssertionError(v.toString());
    }

    @GameTest(maxTicks = 400)
    public void serverHeldRidesKeepTheColourLaw(GameTestHelper helper) {
        var s = rigged(true);
        Session.set(s);
        var player = helper.makeMockServerPlayerInLevel();
        var state = PlayerData.state(player, s.season().orElseThrow());
        helper.assertValueEqual(state.log().custody(), Custody.SERVER, "vanilla players get a server held chain");
        for (Colour colour : Colour.values()) {
            var seen = EnumSet.noneOf(Realm.class);
            for (int i = 0; i < 100; i++) {
                var out = RideCore.ride(player, colour);
                helper.assertTrue(out.refusal().isEmpty(), "refused " + out.refusal());
                var ride = out.ride().orElseThrow();
                helper.assertValueEqual(Colour.ofJacobi(s.world().jacobi(ride.after())), colour, "public colour ride " + ride.index());
                helper.assertValueEqual(ride.reported().colour(), colour, "realm colour ride " + ride.index());
                seen.add(ride.reported());
            }
            helper.assertValueEqual(seen.size(), 2, colour + " realms reached " + seen);
        }
        helper.assertValueEqual(PlayerData.state(player, s.season().get()).log().rides().size(), 200, "rides saved");
        Session.set(null);
        helper.succeed();
    }

    @GameTest
    public void playerHeldDiceNeedAQueuedOpeningThatFollowsTheChain(GameTestHelper helper) {
        var s = rigged(true);
        Session.set(s);
        var player = helper.makeMockServerPlayerInLevel();
        var chain = new SeedChain(new byte[32], 8);
        helper.assertValueEqual(PlayerData.commit(player, chain.commitment()), "committed", "commit");
        helper.assertValueEqual(RideCore.ride(player, Colour.WARM).refusal().orElse(""), PlayerData.NO_OPENING, "nothing queued");
        helper.assertValueEqual(PlayerData.queue(player, chain.opening(1)), "queued", "queue wrong link");
        helper.assertValueEqual(RideCore.ride(player, Colour.WARM).refusal().orElse(""),
                "refused: opening does not follow the chain index=0", "wrong link");
        helper.assertValueEqual(PlayerData.state(player, s.season().get()).log().rides().size(), 0, "refused ride changed state");
        PlayerData.queue(player, chain.opening(0));
        var ok = RideCore.ride(player, Colour.WARM);
        helper.assertTrue(ok.ride().isPresent(), "right link refused " + ok.refusal());
        helper.assertValueEqual(RideCore.ride(player, Colour.WARM).refusal().orElse(""), PlayerData.NO_OPENING, "opening used once");
        helper.assertValueEqual(PlayerData.state(player, s.season().get()).log().custody(), Custody.PLAYER, "custody");
        helper.assertTrue(PlayerData.commit(player, chain.commitment()).startsWith("refused:"), "commit after a ride");
        Session.set(null);
        helper.succeed();
    }

    @GameTest
    public void ridesRefuseWithoutAKey(GameTestHelper helper) {
        Session.set(rigged(false));
        var player = helper.makeMockServerPlayerInLevel();
        helper.assertValueEqual(RideCore.ride(player, Colour.WARM).refusal().orElse(""), RideCore.NO_KEY, "no key");
        Session.set(null);
        helper.assertValueEqual(RideCore.ride(player, Colour.WARM).refusal().orElse(""), RideCore.NO_SESSION, "no session");
        helper.succeed();
    }
}
