package rhworld.engine;

import java.util.SplittableRandom;
import java.util.function.Function;

public final class Props {
    public static final long SEED = Long.getLong("rhworld.seed", 20261127L);

    public interface Check<T> {
        void check(T value) throws Exception;
    }

    private Props() {}

    public static <T> int forAll(String name, int trials, Function<SplittableRandom, T> gen, Check<T> check) {
        SplittableRandom root = new SplittableRandom(SEED ^ name.hashCode());
        for (int i = 0; i < trials; i++) {
            long seed = root.nextLong();
            T value = gen.apply(new SplittableRandom(seed));
            try {
                check.check(value);
            } catch (Throwable e) {
                String shown = String.valueOf(value);
                if (shown.length() > 300) shown = shown.substring(0, 300) + "...";
                throw new AssertionError("[prop] name=" + name + " trial=" + i + " case_seed=" + seed + " run_seed=" + SEED + " value=" + shown
                        + " cause=" + e.getMessage(), e);
            }
        }
        return trials;
    }
}
