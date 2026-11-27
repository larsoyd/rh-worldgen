package rhworld.mechanics;

public enum Custody {
    PLAYER("player"),
    SERVER("server-held");

    private final String label;

    Custody(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Custody ofLabel(String label) {
        for (Custody c : values()) if (c.label.equals(label)) return c;
        throw new IllegalArgumentException("refused: unknown dice custody=" + label);
    }
}
