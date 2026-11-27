package rhworld.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import rhworld.engine.arith.Budget;
import rhworld.engine.arith.Primes;

class SmallWorldsTest {
    private static final long LIMIT = 1_000_000;

    @Test
    void everySmallTwoPrimeKeySpansBothFactors() {
        int[] primes = Primes.upTo(LIMIT / 3);
        int keys = 0;
        int cutFailures = 0;
        long firstCut = 0;
        for (int i = 0; i < primes.length; i++) {
            for (int j = i + 1; j < primes.length && (long) primes[i] * primes[j] < LIMIT; j++) {
                long n = (long) primes[i] * primes[j];
                if (n < 3000 || primes[i] <= Budget.forBits(64 - Long.numberOfLeadingZeros(n))) continue;
                Warden warden = new Warden(BigInteger.valueOf(primes[i]), BigInteger.valueOf(primes[j]));
                WildGate gate = WildGate.forModulus(warden.modulus());
                assertEquals(2, warden.rank(gate.primes()), "n=" + n);
                keys++;
                if (warden.rank(Primes.upTo((long) Math.log(n))) < 2) {
                    cutFailures++;
                    if (firstCut == 0) firstCut = n;
                }
            }
        }
        System.out.println("[small] keys=" + keys + " cut_to_ln_n_failures=" + cutFailures + " first=" + firstCut);
        assertTrue(keys > 1000, "keys=" + keys);
        assertTrue(cutFailures > 0, "a gate cut to primes below ln n never failed keys=" + keys);
    }
}
