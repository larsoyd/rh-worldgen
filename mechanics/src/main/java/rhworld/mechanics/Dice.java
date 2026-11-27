package rhworld.mechanics;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Dice {
    public static final int SEED_BYTES = 32;

    private Dice() {}

    // block b is sha256 of seed, index and b, bits read low first
    public static long[] bits(byte[] seed, long index, int count) {
        if (seed.length != SEED_BYTES) throw new IllegalArgumentException("refused: seed must be 32 bytes length=" + seed.length);
        if (count < 0) throw new IllegalArgumentException("refused: negative bit count count=" + count);
        long[] words = new long[(count + 63) / 64];
        MessageDigest sha = sha256();
        int j = 0;
        for (int block = 0; j < count; block++) {
            sha.update(seed);
            sha.update(ByteBuffer.allocate(12).putLong(index).putInt(block).array());
            for (byte b : sha.digest()) {
                for (int k = 0; k < 8 && j < count; k++, j++) {
                    if ((b >> k & 1) == 1) words[j >>> 6] |= 1L << (j & 63);
                }
            }
        }
        return words;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("sha-256 missing from the runtime", e);
        }
    }
}
