package rhworld.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import rhworld.engine.arith.Arith;
import rhworld.engine.contract.Verdict;

class GoldenRidesTest {
    private static final int RIDES = 8;

    private static final BigInteger M521 = BigInteger.TWO.pow(521).subtract(BigInteger.ONE);
    private static final BigInteger M607 = BigInteger.TWO.pow(607).subtract(BigInteger.ONE);

    private static Colour colour(int r) {
        return (0b10110010 >>> r & 1) == 1 ? Colour.COLD : Colour.WARM;
    }

    private static SeedChain chain() {
        byte[] top = new byte[Dice.SEED_BYTES];
        Arrays.fill(top, (byte) 0x11);
        return new SeedChain(top, RIDES);
    }

    private static RideLog generate(Warden warden) {
        SeedChain c = chain();
        Season season = new Season(warden);
        Season.Player p = season.join(c.commitment(), Custody.PLAYER);
        for (int r = 0; r < RIDES; r++) season.ride(p, colour(r), c.opening(r));
        return p.log();
    }

    // steering fires when the plain product has the wrong colour, recorded with the witness exponent bit
    private static String steering(RideLog log) {
        WildGate gate = WildGate.forModulus(log.modulus());
        int[] primes = gate.primes();
        int w = Arrays.binarySearch(primes, (int) gate.witness());
        StringBuilder s = new StringBuilder();
        for (Ride r : log.rides()) {
            long[] e = Dice.forOpening(r.opening(), r.index(), primes.length);
            BigInteger y = ((Verdict.Ok<BigInteger>) Arith.subsetProduct(e, gate.table(), log.modulus())).value();
            boolean fired = Colour.ofJacobi(Arith.jacobi(r.before().multiply(y), log.modulus())) != r.colour();
            s.append(fired ? ((e[w >>> 6] >>> (w & 63) & 1) == 1 ? '1' : '0') : '-');
        }
        return s.toString();
    }

    private static void pin(String name, Warden warden) throws IOException {
        RideLog log = generate(warden);
        Path file = Path.of("..", "samples", "rides-" + name + ".json");
        if ("1".equals(System.getenv("RHWORLD_WRITE_GOLDEN"))) Files.writeString(file, log.toJson() + "\n");
        String steer = steering(log);
        System.out.println("[golden-rides] name=" + name + " rides=" + RIDES + " steering=" + steer);
        assertEquals(Files.readString(file).strip(), log.toJson(), "file=" + file);
        assertEquals(Optional.empty(), Replay.check(RideLog.fromJson(Files.readString(file))));
        assertTrue(steer.contains("0") && steer.contains("1"), "steering pattern=" + steer + " needs both witness bits");
    }

    @Test
    void mersenneLogIsPinned() throws IOException {
        pin("mersenne", new Warden(M521, M607));
    }

    @Test
    void riggedLogIsPinned() throws IOException {
        pin("rigged-1024", Keys.sample("rigged-1024"));
    }

    @Test
    void randomLogIsPinned() throws IOException {
        pin("random-1024", Keys.sample("random-1024"));
    }
}
