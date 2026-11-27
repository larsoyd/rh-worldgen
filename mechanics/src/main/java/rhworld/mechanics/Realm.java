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

    // bit 0 is the p symbol and bit 1 the q symbol, set when the symbol is -1
    public static Realm ofState(int state) {
        if (state < 0 || state > 3) throw new IllegalArgumentException("refused: state outside v4 state=" + state);
        return of((state & 1) == 1, (state & 2) == 2);
    }

    public int state() {
        return (flipP ? 1 : 0) | (flipQ ? 2 : 0);
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
