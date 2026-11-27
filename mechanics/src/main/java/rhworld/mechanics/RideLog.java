package rhworld.mechanics;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import rhworld.engine.json.Json;

public final class RideLog {
    public static final String FORMAT = "rhworld.rides/1";

    private final BigInteger n;
    private final String commitment;
    private final List<Ride> rides = new ArrayList<>();

    public RideLog(BigInteger n, String commitment) {
        this.n = n;
        this.commitment = commitment;
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
        return Map.of("format", FORMAT, "n", n.toString(), "commitment", commitment, "rides", out);
    }

    public static RideLog fromJson(String text) {
        return fromMap((Map<?, ?>) Json.parse(text));
    }

    static RideLog fromMap(Map<?, ?> root) {
        if (!FORMAT.equals(root.get("format"))) throw new IllegalArgumentException("refused: unknown ride log format=" + root.get("format"));
        RideLog log = new RideLog(new BigInteger((String) root.get("n")), (String) root.get("commitment"));
        for (Object o : (List<?>) root.get("rides")) {
            Map<?, ?> m = (Map<?, ?>) o;
            log.add(new Ride(((BigInteger) m.get("index")).longValueExact(),
                    Colour.valueOf(upper(m.get("colour"))),
                    (String) m.get("opening"),
                    new BigInteger((String) m.get("before")),
                    new BigInteger((String) m.get("after")),
                    Realm.valueOf(upper(m.get("reported")))));
        }
        return log;
    }

    private static String name(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    private static String upper(Object s) {
        return ((String) s).toUpperCase(Locale.ROOT);
    }
}
