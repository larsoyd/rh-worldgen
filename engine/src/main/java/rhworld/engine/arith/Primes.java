package rhworld.engine.arith;

import java.util.Arrays;
import java.util.BitSet;

public final class Primes {
    public static final long MAX_LIMIT = Integer.MAX_VALUE - 1;

    private record Table(long limit, int[] primes) {}

    private static volatile Table table = new Table(1, new int[0]);

    private Primes() {}

    public static int[] upTo(long limit) {
        if (limit > MAX_LIMIT) throw new IllegalArgumentException("prime limit too large limit=" + limit);
        Table t = table;
        if (limit > t.limit) {
            int size = (int) Math.min(MAX_LIMIT, Math.max(limit, 2 * t.limit));
            t = new Table(size, sieve(size));
            synchronized (Primes.class) {
                if (t.limit > table.limit) table = t;
            }
        }
        return prefix(t.primes, limit);
    }

    private static int[] prefix(int[] primes, long limit) {
        int i = Arrays.binarySearch(primes, (int) Math.max(0, limit));
        return Arrays.copyOf(primes, i >= 0 ? i + 1 : -i - 1);
    }

    private static int[] sieve(int limit) {
        BitSet composite = new BitSet(limit + 1);
        for (long i = 2; i * i <= limit; i++) {
            if (!composite.get((int) i)) {
                for (long j = i * i; j <= limit; j += i) composite.set((int) j);
            }
        }
        int[] out = new int[limit - 1 - composite.cardinality()];
        int k = 0;
        for (int i = 2; i <= limit; i++) if (!composite.get(i)) out[k++] = i;
        return out;
    }
}
