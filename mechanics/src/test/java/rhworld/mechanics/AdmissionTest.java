package rhworld.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import rhworld.engine.contract.Verdict;
import rhworld.engine.telemetry.Telemetry;

class AdmissionTest {
    private static final Warden RIGGED = Keys.sample("rigged-1024");

    private static String reason(Verdict<?> v) {
        return v instanceof Verdict.Refused<?> r ? r.toString() : "ok";
    }

    @Test
    void seasonOpensFromARevealedWorld() {
        Verdict<Season> v = Season.reveal(RIGGED.modulus(), RIGGED.q(), RIGGED.p(), Telemetry.NONE);
        Season season = ((Verdict.Ok<Season>) v).value();
        Season.Player player = season.join(new SeedChain(new byte[32], 2).commitment(), Custody.PLAYER);
        assertEquals(Realm.HEARTH, season.realm(player));
        assertEquals(RIGGED.modulus(), season.modulus());
    }

    @Test
    void seasonReplayNamesTheFirstFalseReport() {
        Season season = ((Verdict.Ok<Season>) Season.reveal(RIGGED.modulus(), RIGGED.p(), RIGGED.q(), Telemetry.NONE)).value();
        SeedChain c = new SeedChain(new byte[32], 6);
        Season.Player player = season.join(c.commitment(), Custody.PLAYER);
        for (int r = 0; r < 6; r++) season.ride(player, r % 2 == 0 ? Colour.WARM : Colour.COLD, c.opening(r));
        assertEquals(OptionalLong.empty(), season.replay(player.log()));
        RideLog lie = new RideLog(player.log().modulus(), player.log().commitment(), Custody.PLAYER);
        for (Ride r : player.log().rides())
            lie.add(r.index() == 3 ? new Ride(r.index(), r.colour(), r.opening(), r.before(), r.after(), r.reported().plus(Realm.EMBER)) : r);
        assertEquals(OptionalLong.of(3), season.replay(lie));
        Season other = new Season(Keys.sample("random-1024"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> other.replay(player.log()));
        assertEquals("refused: log modulus differs from the revealed key", e.getMessage());
    }

    @Test
    void badKeysRefuseWithTheEngineReasons() {
        assertEquals("refused: modulus too small or even", reason(Season.reveal(BigInteger.valueOf(15), BigInteger.valueOf(3), BigInteger.valueOf(5), Telemetry.NONE)));
        BigInteger n = RIGGED.modulus();
        assertEquals("refused: factors do not multiply to n", reason(Season.reveal(n, RIGGED.p(), RIGGED.p(), Telemetry.NONE)));
    }

    @Test
    void replayRefusesAnInadmissibleModulus() {
        RideLog log = new RideLog(BigInteger.valueOf(15), new SeedChain(new byte[32], 1).commitment(), Custody.PLAYER);
        assertEquals(Optional.of("refused: modulus too small or even"), Replay.check(log));
    }
}
