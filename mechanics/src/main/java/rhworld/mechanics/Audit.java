package rhworld.mechanics;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import rhworld.engine.arith.Arith;

public final class Audit {
    public record Verdict(Map<Colour, Integer> rides, Map<Realm, Integer> arrivals, int publicMismatches, Realm weakest,
            double pValue, boolean accused, Custody custody) {
        public Object[] fields() {
            List<Object> f = new ArrayList<>(List.of("rides_warm", rides.get(Colour.WARM), "rides_cold", rides.get(Colour.COLD)));
            for (Realm r : Realm.values()) f.addAll(List.of(lower(r), arrivals.get(r)));
            f.addAll(List.of("mismatches", publicMismatches, "weakest", lower(weakest), "p", String.valueOf(pValue), "verdict", accused ? "accused" : "clear",
                    "dice", custody.label()));
            if (custody == Custody.SERVER) f.addAll(List.of("condition8", "not_met"));
            return f.toArray();
        }

        public String line() {
            StringBuilder s = new StringBuilder("[audit]");
            Object[] f = fields();
            for (int i = 0; i < f.length; i += 2) s.append(' ').append(f[i]).append('=').append(f[i + 1]);
            return s.toString();
        }
    }

    private Audit() {}

    // an honest report is a fair coin within the ride colour for every key that passes the checks
    public static Verdict of(RideLog log, double threshold) {
        Map<Colour, Integer> rides = new EnumMap<>(Colour.class);
        Map<Realm, Integer> arrivals = new EnumMap<>(Realm.class);
        for (Colour c : Colour.values()) rides.put(c, 0);
        for (Realm r : Realm.values()) arrivals.put(r, 0);
        int mismatches = 0;
        for (Ride ride : log.rides()) {
            Colour shown = Colour.ofJacobi(Arith.jacobi(ride.after(), log.modulus()));
            if (shown != ride.colour() || ride.reported().colour() != shown) mismatches++;
            rides.merge(ride.colour(), 1, Integer::sum);
            arrivals.merge(ride.reported(), 1, Integer::sum);
        }
        Realm weakest = Realm.HEARTH;
        double best = 2;
        for (Realm r : Realm.values()) {
            double tail = lowerTail(rides.get(r.colour()), arrivals.get(r));
            if (tail < best) {
                best = tail;
                weakest = r;
            }
        }
        double p = Math.min(1, 4 * best);
        return new Verdict(rides, arrivals, mismatches, weakest, p, mismatches > 0 || p <= threshold, log.custody());
    }

    static OptionalLong replay(Warden warden, RideLog log) {
        if (!warden.modulus().equals(log.modulus())) throw new IllegalArgumentException("refused: log modulus differs from the revealed key");
        for (Ride ride : log.rides()) {
            if (warden.realm(ride.after()) != ride.reported()) return OptionalLong.of(ride.index());
        }
        return OptionalLong.empty();
    }

    private static double lowerTail(int trials, int hits) {
        BigInteger sum = BigInteger.ZERO;
        BigInteger term = BigInteger.ONE;
        for (int i = 0; i <= Math.min(hits, trials); i++) {
            sum = sum.add(term);
            term = term.multiply(BigInteger.valueOf(trials - i)).divide(BigInteger.valueOf(i + 1));
        }
        return new BigDecimal(sum).divide(new BigDecimal(BigInteger.ONE.shiftLeft(trials)), MathContext.DECIMAL64).doubleValue();
    }

    private static String lower(Realm r) {
        return r.name().toLowerCase(Locale.ROOT);
    }
}
