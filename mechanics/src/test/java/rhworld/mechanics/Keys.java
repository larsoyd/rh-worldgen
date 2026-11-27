package rhworld.mechanics;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import rhworld.engine.json.Json;

final class Keys {
    private Keys() {}

    static Warden sample(String name) {
        Path file = Path.of("..", "samples", name, "gm-key.json");
        try {
            Map<?, ?> key = (Map<?, ?>) Json.parse(Files.readString(file));
            return new Warden(new BigInteger((String) key.get("p")), new BigInteger((String) key.get("q")));
        } catch (IOException e) {
            throw new UncheckedIOException("sample key missing file=" + file.toAbsolutePath(), e);
        }
    }
}
