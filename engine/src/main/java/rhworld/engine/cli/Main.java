package rhworld.engine.cli;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.exit(Cli.run(args, System.out, System.err));
    }
}
