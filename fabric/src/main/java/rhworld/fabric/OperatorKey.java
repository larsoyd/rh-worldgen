package rhworld.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

public final class OperatorKey {
    private static volatile OperatorKey loaded;

    private final BigInteger n;
    private final BigInteger p;
    private final BigInteger q;

    private OperatorKey(BigInteger n, BigInteger p, BigInteger q) {
        this.n = n;
        this.p = p;
        this.q = q;
    }

    public static Optional<OperatorKey> current() {
        return Optional.ofNullable(loaded);
    }

    public BigInteger n() { return n; }
    public BigInteger p() { return p; }
    public BigInteger q() { return q; }

    @Override
    public String toString() {
        return "OperatorKey[bits=" + n.bitLength() + "]";
    }

    static String load(Path file, Set<BigInteger> moduli) {
        loaded = null;
        if (!Files.exists(file)) return "ok";
        OperatorKey key;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            key = new OperatorKey(number(json, "n"), number(json, "p"), number(json, "q"));
        } catch (IOException | JsonParseException | IllegalStateException | NumberFormatException e) {
            return "refused: key file unreadable";
        }
        if (!key.p.multiply(key.q).equals(key.n) || !moduli.equals(Set.of(key.n))) return "refused: key file mismatch";
        loaded = key;
        return "ok";
    }

    private static BigInteger number(JsonObject json, String field) {
        if (!json.has(field)) throw new IllegalStateException("missing " + field);
        BigInteger v = new BigInteger(json.get(field).getAsString());
        if (v.signum() <= 0) throw new NumberFormatException(field);
        return v;
    }
}
