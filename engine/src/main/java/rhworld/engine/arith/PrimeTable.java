package rhworld.engine.arith;

public final class PrimeTable {
    private final int[] primes;
    private final long limit;

    private PrimeTable(int[] primes, long limit) {
        this.primes = primes;
        this.limit = limit;
    }

    public static PrimeTable upTo(long limit) {
        if (limit < 2) throw new IllegalArgumentException("prime table needs a limit of at least 2 limit=" + limit);
        return new PrimeTable(Primes.upTo(limit), limit);
    }

    public int size() {
        return primes.length;
    }

    public int get(int i) {
        return primes[i];
    }

    public long limit() {
        return limit;
    }

    public int[] toArray() {
        return primes.clone();
    }

    int[] array() {
        return primes;
    }
}
