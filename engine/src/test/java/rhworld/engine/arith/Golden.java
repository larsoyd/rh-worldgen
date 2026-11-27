package rhworld.engine.arith;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import rhworld.engine.json.Json;

final class Golden {
    private static Map<String, Object> data;

    private Golden() {}

    @SuppressWarnings("unchecked")
    static synchronized <T> List<T> list(String key) {
        if (data == null) {
            try (InputStream in = Golden.class.getResourceAsStream("/golden/oracle.json")) {
                if (in == null) throw new IllegalStateException("golden vectors missing on test classpath");
                data = (Map<String, Object>) Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        Object v = data.get(key);
        if (v == null) throw new IllegalStateException("golden key missing " + key);
        return (List<T>) v;
    }
}
