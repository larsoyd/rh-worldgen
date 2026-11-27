package rhworld.fabric;

import java.math.BigInteger;
import java.util.Optional;
import rhworld.engine.cert.Certificate;
import rhworld.engine.contract.Verdict;
import rhworld.engine.telemetry.Telemetry;
import rhworld.engine.world.PublicWorld;
import rhworld.mechanics.Season;

public record Session(PublicWorld world, Optional<Season> season, String certificate, String worldId) {
    private static volatile Session current;

    public static final Telemetry TELEMETRY = (tag, fields) -> {
        var kv = new Object[fields.size() * 2];
        int i = 0;
        for (var e : fields.entrySet()) {
            kv[i++] = e.getKey();
            kv[i++] = e.getValue();
        }
        Log.line("engine." + tag, kv);
    };

    public static Verdict<Session> open(BigInteger n, Optional<BigInteger[]> factors) {
        return PublicWorld.open(n, TELEMETRY).then(world -> {
            var cert = Certificate.of(world);
            if (factors.isEmpty()) return new Verdict.Ok<>(new Session(world, Optional.empty(), cert.json(), cert.worldId()));
            var f = factors.get();
            return Season.reveal(n, f[0], f[1], TELEMETRY)
                    .map(season -> new Session(world, Optional.of(season), cert.json(), cert.worldId()));
        });
    }

    public static Optional<Session> current() {
        return Optional.ofNullable(current);
    }

    static void set(Session session) {
        current = session;
    }
}
