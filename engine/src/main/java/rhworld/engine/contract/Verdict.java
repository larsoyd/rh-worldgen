package rhworld.engine.contract;

import java.util.function.Function;

public sealed interface Verdict<T> {
    record Ok<T>(T value) implements Verdict<T> {}

    record Refused<T>(String reason) implements Verdict<T> {
        public Refused {
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("refusal needs a reason");
        }

        @Override
        public String toString() {
            return "refused: " + reason;
        }
    }

    default <R> Verdict<R> map(Function<? super T, ? extends R> f) {
        return switch (this) {
            case Ok<T> ok -> new Ok<>(f.apply(ok.value()));
            case Refused<T> r -> new Refused<>(r.reason());
        };
    }

    default <R> Verdict<R> then(Function<? super T, Verdict<R>> f) {
        return switch (this) {
            case Ok<T> ok -> f.apply(ok.value());
            case Refused<T> r -> new Refused<>(r.reason());
        };
    }
}
