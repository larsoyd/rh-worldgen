package rhworld.mechanics;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import rhworld.engine.json.Json;
import rhworld.engine.json.JsonException;

public final class RideLog {
    public static final String FORMAT = "rhworld.rides/1";

    private final BigInteger n;
    private final String commitment;
    private final Custody custody;
    private final List<Ride> rides = new ArrayList<>();

    public RideLog(BigInteger n, String commitment, Custody custody) {
        this.n = n;
        this.commitment = commitment;
        this.custody = custody;
    }

    public Custody custody() {
        return custody;
    }

    public String commitment() {
        return commitment;
    }

    public BigInteger modulus() {
        return n;
    }

    public void add(Ride ride) {
        if (ride.index() != rides.size())
            throw new IllegalArgumentException("refused: ride index out of order index=" + ride.index() + " expected=" + rides.size());
        String previous = rides.isEmpty() ? commitment : rides.getLast().opening();
        if (!SeedChain.follows(previous, ride.opening()))
            throw new IllegalArgumentException("refused: opening does not follow the chain index=" + ride.index());
        if (!rides.isEmpty() && !rides.getLast().after().equals(ride.before()))
            throw new IllegalArgumentException("refused: ride does not start where the last ended index=" + ride.index());
        rides.add(ride);
    }

    public List<Ride> rides() {
        return List.copyOf(rides);
    }

    public String toJson() {
        return Json.write(toMap());
    }

    Map<String, Object> toMap() {
        List<Object> out = new ArrayList<>();
        for (Ride r : rides) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", r.index());
            m.put("colour", name(r.colour()));
            m.put("opening", r.opening());
            m.put("before", r.before().toString());
            m.put("after", r.after().toString());
            m.put("reported", name(r.reported()));
            out.add(m);
        }
        return Map.of("format", FORMAT, "n", n.toString(), "commitment", commitment, "dice", custody.label(), "rides", out);
    }

    public static RideLog fromJson(String text) {
        Object root;
        try {
            root = Json.parse(text);
        } catch (JsonException e) {
            throw new IllegalArgumentException("refused: ride log is not json");
        }
        if (!Json.write(root).equals(text)) throw new IllegalArgumentException("refused: ride log is not canonical json");
        if (!(root instanceof Map<?, ?> m)) throw malformed("root");
        return fromMap(m);
    }

    static RideLog fromMap(Map<?, ?> root) {
        if (!FORMAT.equals(root.get("format"))) throw new IllegalArgumentException("refused: unknown ride log format=" + root.get("format"));
        RideLog log = new RideLog(integer(root, "n"), field(root, "commitment", String.class), Custody.ofLabel(field(root, "dice", String.class)));
        for (Object o : field(root, "rides", List.class)) {
            if (!(o instanceof Map<?, ?> m)) throw malformed("rides");
            BigInteger index = field(m, "index", BigInteger.class);
            log.add(new Ride(index.longValueExact(), value(Colour.class, m, "colour"),
                    field(m, "opening", String.class), integer(m, "before"), integer(m, "after"),
                    value(Realm.class, m, "reported")));
        }
        return log;
    }

    private static <T> T field(Map<?, ?> m, String key, Class<T> type) {
        Object v = m.get(key);
        if (!type.isInstance(v)) throw malformed(key);
        return type.cast(v);
    }

    private static BigInteger integer(Map<?, ?> m, String key) {
        String s = field(m, key, String.class);
        if (!s.matches("0|[1-9][0-9]*")) throw malformed(key);
        return new BigInteger(s);
    }

    private static IllegalArgumentException malformed(String key) {
        return new IllegalArgumentException("refused: malformed ride log field=" + key);
    }

    private static String name(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    private static <E extends Enum<E>> E value(Class<E> type, Map<?, ?> m, String key) {
        String s = field(m, key, String.class);
        for (E e : type.getEnumConstants()) if (name(e).equals(s)) return e;
        throw malformed(key);
    }
}
