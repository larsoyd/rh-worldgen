package rhworld.mechanics;

import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import rhworld.engine.contract.Verdict;
import rhworld.engine.json.Json;
import rhworld.engine.telemetry.Telemetry;
import rhworld.engine.world.GmWorld;
import rhworld.engine.world.PublicWorld;

// the only place the hidden half is read, and only at the attunement a ride produced
public final class Season {
    public static final String FORMAT = "rhworld.player/1";

    public static final class Player {
        private BigInteger x;
        private final RideLog log;
        private final Concord concord;

        private Player(BigInteger x, RideLog log, Concord concord) {
            this.x = x;
            this.log = log;
            this.concord = concord;
        }

        public BigInteger attunement() {
            return x;
        }

        public RideLog log() {
            return log;
        }

        public Concord concord() {
            return concord;
        }

        public String toJson() {
            return Json.write(Map.of("format", FORMAT, "attunement", x.toString(), "log", log.toMap(), "concord", concord.toMap()));
        }
    }

    private final Warden warden;
    private final WildGate gate;

    Season(Warden warden) {
        this.warden = warden;
        this.gate = WildGate.forModulus(warden.modulus());
    }

    public Season(GmWorld world) {
        this.warden = new Warden(world.p(), world.q());
        this.gate = WildGate.of(world.world());
    }

    public static Verdict<Season> reveal(BigInteger n, BigInteger p, BigInteger q, Telemetry t) {
        return PublicWorld.open(n, t).then(w -> GmWorld.reveal(w, p, q, Optional.empty(), t)).map(Season::new);
    }

    public BigInteger modulus() {
        return warden.modulus();
    }

    public Player join(String commitment, Custody custody) {
        return new Player(BigInteger.ONE, new RideLog(warden.modulus(), commitment, custody), new Concord());
    }

    public Ride ride(Player player, Colour colour, String opening) {
        long index = player.log.rides().size();
        BigInteger after = gate.ride(player.x, colour, Dice.forOpening(opening, index, gate.size()));
        Ride ride = new Ride(index, colour, opening, player.x, after, warden.realm(after));
        player.log.add(ride);
        player.x = after;
        return ride;
    }

    public Realm realm(Player player) {
        return warden.realm(player.x);
    }

    public OptionalLong replay(RideLog log) {
        return Audit.replay(warden, log);
    }

    public boolean collect(Player player) {
        return player.concord.grant(realm(player));
    }

    public Player restore(String json) {
        Map<?, ?> root = (Map<?, ?>) Json.parse(json);
        if (!FORMAT.equals(root.get("format"))) throw new IllegalArgumentException("refused: unknown player format=" + root.get("format"));
        RideLog log = RideLog.fromMap((Map<?, ?>) root.get("log"));
        if (!log.modulus().equals(warden.modulus())) throw new IllegalArgumentException("refused: player state belongs to another key");
        BigInteger x = new BigInteger((String) root.get("attunement"));
        BigInteger expected = log.rides().isEmpty() ? BigInteger.ONE : log.rides().getLast().after();
        if (!x.equals(expected)) throw new IllegalArgumentException("refused: attunement is not where the last ride ended");
        return new Player(x, log, Concord.fromMap((Map<?, ?>) root.get("concord")));
    }
}
