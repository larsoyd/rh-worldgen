package rhworld.mechanics;

public enum Realm {
    HEARTH(false, false),
    EMBER(true, true),
    MIST(false, true),
    FROST(true, false);

    private final boolean flipP;
    private final boolean flipQ;

    Realm(boolean flipP, boolean flipQ) {
        this.flipP = flipP;
        this.flipQ = flipQ;
    }

    public static Realm of(int symbolP, int symbolQ) {
        if (Math.abs(symbolP) != 1 || Math.abs(symbolQ) != 1)
            throw new IllegalArgumentException("refused: symbol not a unit p=" + symbolP + " q=" + symbolQ);
        return of(symbolP < 0, symbolQ < 0);
    }

    static Realm of(boolean flipP, boolean flipQ) {
        for (Realm r : values()) if (r.flipP == flipP && r.flipQ == flipQ) return r;
        throw new AssertionError();
    }

    public Colour colour() {
        return flipP == flipQ ? Colour.WARM : Colour.COLD;
    }

    public Realm plus(Realm shift) {
        return of(flipP ^ shift.flipP, flipQ ^ shift.flipQ);
    }

    boolean flipP() {
        return flipP;
    }

    boolean flipQ() {
        return flipQ;
    }
}
