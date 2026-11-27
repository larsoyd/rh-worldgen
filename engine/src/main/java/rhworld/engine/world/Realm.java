package rhworld.engine.world;

public record Realm(int p, int q) {
    public int state() {
        return (p == -1 ? 1 : 0) | (q == -1 ? 2 : 0);
    }
}
