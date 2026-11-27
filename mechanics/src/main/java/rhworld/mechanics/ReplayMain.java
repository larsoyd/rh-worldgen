package rhworld.mechanics;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class ReplayMain {
    private static final double THRESHOLD = 0x1p-30;

    private ReplayMain() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out));
    }

    static int run(String[] args, PrintStream out) {
        if (args.length != 2 || !args[0].equals("--log")) return fail(out, 1, "refused: usage replay --log <file>");
        String text;
        try {
            text = Files.readString(Path.of(args[1]));
        } catch (IOException e) {
            return fail(out, 1, "refused: cannot read log file=" + args[1]);
        }
        RideLog log;
        try {
            log = RideLog.fromJson(text);
        } catch (RuntimeException e) {
            return fail(out, 2, e.getMessage() != null && e.getMessage().startsWith("refused: ") ? e.getMessage() : "refused: malformed log " + e);
        }
        out.println("[replay] rides=" + log.rides().size() + " dice=" + log.custody().label() + " n_bits=" + log.modulus().bitLength());
        out.println(Audit.of(log, THRESHOLD).line());
        Optional<String> bad = Replay.check(log);
        if (bad.isPresent()) return fail(out, 2, bad.get());
        out.println("[replay] result=ok");
        return 0;
    }

    private static int fail(PrintStream out, int code, String reason) {
        out.println(reason);
        return code;
    }
}
