package rhworld.fabric;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import rhworld.engine.arith.Arith;
import rhworld.mechanics.Colour;
import rhworld.mechanics.Realm;
import rhworld.mechanics.Warden;

public final class RideCoreTest {
    static final byte[] SEED = new byte[32];

    static Session rigged(boolean withKey) {
        try (var in = RideCoreTest.class.getResourceAsStream("/rhworld-test/rigged-key.json")) {
            var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            var p = new BigInteger(json.get("p").getAsString());
            var q = new BigInteger(json.get("q").getAsString());
            return Session.create(p.multiply(q), withKey ? Optional.of(new Warden(p, q)) : Optional.empty());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @GameTest(maxTicks = 400)
    public void ridesKeepTheColourLawAndReachBothRealmsOfTheColour(GameTestHelper helper) {
        var s = rigged(true);
        for (Colour colour : Colour.values()) {
            var seen = EnumSet.noneOf(Realm.class);
            BigInteger x = BigInteger.ONE;
            for (int i = 0; i < 200; i++) {
                SEED[0] = (byte) i;
                SEED[1] = (byte) colour.ordinal();
                var out = RideCore.ride(s, x, colour, SEED, i);
                helper.assertTrue(out.refusal().isEmpty(), "refused " + out.refusal());
                var ride = out.ride().orElseThrow();
                helper.assertValueEqual(Colour.ofJacobi(Arith.jacobi(ride.after(), s.n())), colour, "public colour ride " + i);
                helper.assertValueEqual(ride.reported().colour(), colour, "realm colour ride " + i);
                helper.assertValueEqual(ride.before(), x, "chain ride " + i);
                seen.add(ride.reported());
                x = ride.after();
            }
            helper.assertValueEqual(seen.size(), 2, colour + " realms reached " + seen);
        }
        helper.succeed();
    }

    @GameTest
    public void sameSeedAndIndexGiveTheSameRide(GameTestHelper helper) {
        var s = rigged(true);
        var a = RideCore.ride(s, BigInteger.TWO, Colour.COLD, SEED, 7).ride().orElseThrow();
        var b = RideCore.ride(s, BigInteger.TWO, Colour.COLD, SEED, 7).ride().orElseThrow();
        var c = RideCore.ride(s, BigInteger.TWO, Colour.COLD, SEED, 8).ride().orElseThrow();
        helper.assertValueEqual(a, b, "replay");
        helper.assertFalse(a.after().equals(c.after()), "index must change the ride");
        helper.succeed();
    }

    @GameTest
    public void ridesRefuseWithoutKeySeedOrUnit(GameTestHelper helper) {
        var keyed = rigged(true);
        helper.assertValueEqual(RideCore.ride(rigged(false), BigInteger.ONE, Colour.WARM, SEED, 0).refusal(),
                Optional.of("refused: no operator key on this server"), "no key");
        helper.assertValueEqual(RideCore.ride(keyed, BigInteger.ONE, Colour.WARM, null, 0).refusal(),
                Optional.of("refused: no dice seed, set one with /rhworld dice <64 hex digits>"), "no seed");
        helper.assertValueEqual(RideCore.ride(keyed, BigInteger.ZERO, Colour.WARM, SEED, 0).refusal(),
                Optional.of("refused: attunement not a unit below n"), "zero x");
        helper.assertValueEqual(RideCore.ride(keyed, keyed.n(), Colour.WARM, SEED, 0).refusal(),
                Optional.of("refused: attunement not a unit below n"), "x = n");
        helper.succeed();
    }
}
