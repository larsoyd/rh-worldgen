package rhworld.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Optional;
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
