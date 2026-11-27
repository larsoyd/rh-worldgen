package rhworld.mechanics;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

public final class SeedChain {
    private static final HexFormat HEX = HexFormat.of();

    private final byte[][] links;

    // link 0 is the commitment and ride i opens link i plus one, each link the hash of the next
    public SeedChain(byte[] top, int rides) {
        if (top.length != Dice.SEED_BYTES) throw new IllegalArgumentException("refused: chain top must be 32 bytes length=" + top.length);
        if (rides < 1) throw new IllegalArgumentException("refused: chain needs a ride rides=" + rides);
        links = new byte[rides + 1][];
        links[rides] = top.clone();
        MessageDigest sha = Dice.sha256();
        for (int i = rides - 1; i >= 0; i--) links[i] = sha.digest(links[i + 1]);
    }

    public String commitment() {
        return HEX.formatHex(links[0]);
    }

    public String opening(long ride) {
        if (ride < 0 || ride >= links.length - 1)
            throw new IllegalArgumentException("refused: seed chain exhausted ride=" + ride + " length=" + (links.length - 1));
        return HEX.formatHex(links[(int) ride + 1]);
    }

    public static boolean follows(String previous, String opening) {
        byte[] open = parse(opening);
        byte[] prev = parse(previous);
        return open != null && prev != null && MessageDigest.isEqual(Dice.sha256().digest(open), prev);
    }

    static byte[] parse(String hex) {
        if (hex.length() != 2 * Dice.SEED_BYTES || !hex.equals(hex.toLowerCase(Locale.ROOT))) return null;
        try {
            return HEX.parseHex(hex);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
