package rhworld.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

// reads the factors only, every check on them is the engine's
public final class OperatorKey {
    public record Loaded(Optional<BigInteger[]> factors, Optional<String> refusal) {}

    private OperatorKey() {}

    static Loaded read(Path file) {
        if (!Files.exists(file)) return new Loaded(Optional.empty(), Optional.empty());
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            return new Loaded(Optional.of(new BigInteger[] {number(json, "p"), number(json, "q")}), Optional.empty());
        } catch (IOException | JsonParseException | IllegalStateException | NumberFormatException e) {
            return new Loaded(Optional.empty(), Optional.of("refused: key file unreadable"));
        }
    }

    private static BigInteger number(JsonObject json, String field) {
        if (!json.has(field)) throw new IllegalStateException("missing " + field);
        BigInteger v = new BigInteger(json.get(field).getAsString());
        if (v.signum() <= 0) throw new NumberFormatException(field);
        return v;
    }
}
