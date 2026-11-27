package rhworld.mechanics;

public enum Colour {
    WARM,
    COLD;

    public static Colour ofJacobi(int symbol) {
        return switch (symbol) {
            case 1 -> WARM;
            case -1 -> COLD;
            default -> throw new IllegalArgumentException("refused: jacobi symbol not a unit symbol=" + symbol);
        };
    }
}
