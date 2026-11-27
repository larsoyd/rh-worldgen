package rhworld.engine.telemetry;

import java.io.PrintStream;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import rhworld.engine.json.Json;

public interface Telemetry {
    Telemetry NONE = (tag, fields) -> {};

    void emit(String tag, Map<String, Object> fields);

    default void event(String tag, Object... keyValues) {
        if (keyValues.length % 2 != 0) throw new IllegalArgumentException("odd field list for tag=" + tag);
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) fields.put((String) keyValues[i], keyValues[i + 1]);
        emit(tag, Collections.unmodifiableMap(fields));
    }

    static Telemetry lines(PrintStream out) {
        return (tag, fields) -> {
            StringBuilder sb = new StringBuilder("[").append(tag).append(']');
            fields.forEach((k, v) -> sb.append(' ').append(k).append('=').append(plain(v)));
            out.println(sb);
        };
    }

    static Telemetry jsonLines(PrintStream out) {
        return (tag, fields) -> {
            Map<String, Object> m = new TreeMap<>();
            fields.forEach((k, v) -> m.put(k, v instanceof BigInteger b ? b.toString() : v));
            m.put("tag", tag);
            out.println(Json.write(m));
        };
    }

    static Telemetry tee(Telemetry a, Telemetry b) {
        return (tag, fields) -> {
            a.emit(tag, fields);
            b.emit(tag, fields);
        };
    }

    private static String plain(Object v) {
        String s = String.valueOf(v);
        return v instanceof String && (s.isEmpty() || s.contains(" ") || s.contains("\"")) ? Json.write(s) : s;
    }
}
