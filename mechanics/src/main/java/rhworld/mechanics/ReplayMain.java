package rhworld.mechanics;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import rhworld.engine.telemetry.Telemetry;

final class ReplayMain {
    static final String USAGE = "usage: rhworld replay --log <file> [--telemetry lines|json]";
    private static final double THRESHOLD = 0x1p-30;

    private ReplayMain() {}

    static int run(String[] args, PrintStream out, PrintStream err) {
        String file = null;
        String mode = "lines";
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--log" -> file = args[i + 1];
                case "--telemetry" -> mode = args[i + 1];
                default -> {
                    return usage(err, "unknown option " + args[i]);
                }
            }
        }
        if (args.length % 2 != 0 || file == null) return usage(err, "replay needs --log <file>");
        if (!mode.equals("lines") && !mode.equals("json")) return usage(err, "telemetry must be lines or json");
        boolean json = mode.equals("json");
        Telemetry t = json ? Telemetry.jsonLines(err) : Telemetry.lines(err);
        String text;
        try {
            text = Files.readString(Path.of(file));
        } catch (IOException e) {
            err.println("error: io cannot read log file=" + file);
            return 1;
        }
        RideLog log;
        try {
            log = RideLog.fromJson(text);
        } catch (RuntimeException e) {
            String m = e.getMessage();
            return refused(t, json, err, m != null && m.startsWith("refused: ") ? m.substring(9) : "malformed log " + e);
        }
        t.event("replay", "rides", log.rides().size(), "dice", log.custody().label(), "n_bits", log.modulus().bitLength());
        t.event("audit", Audit.of(log, THRESHOLD).fields());
        Optional<String> bad = Replay.check(log);
        if (bad.isPresent()) return refused(t, json, err, bad.get().substring(9));
        t.event(json ? "verdict" : "rhworld", "verdict", "ok", "rides", log.rides().size());
        return 0;
    }

    private static int refused(Telemetry t, boolean json, PrintStream err, String reason) {
        if (json) t.event("verdict", "verdict", "refused", "reason", reason);
        else err.println("refused: " + reason);
        return 2;
    }

    private static int usage(PrintStream err, String message) {
        err.println(USAGE);
        err.println("error: " + message);
        return 1;
    }
}
