package rhworld.mechanics;

import java.io.PrintStream;
import java.util.Arrays;
import rhworld.engine.cli.Cli;

public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0) err.println(ReplayMain.USAGE);
        if (args.length > 0 && args[0].equals("replay")) return ReplayMain.run(Arrays.copyOfRange(args, 1, args.length), out, err);
        return Cli.run(args, out, err);
    }
}
