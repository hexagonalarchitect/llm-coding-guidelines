package starter.kernel;

import java.util.Objects;
import java.util.function.Function;

/**
 * The outcome of an operation that can be refused: a value or a typed error (C1, C11).
 *
 * <p>Refusals are values, not exceptions, and neither side is ever {@code null}. Callers branch
 * with an exhaustive {@code switch} over the two cases, with no {@code default} arm (C12).
 *
 * @param <T> the value on success
 * @param <E> the typed error on refusal
 */
public sealed interface Result<T, E> {

    /**
     * The operation succeeded.
     *
     * @param value the value, never null
     * @param <T> the value type
     * @param <E> the error type
     */
    record Ok<T, E>(T value) implements Result<T, E> {
        /** Rejects a null value: null is a defect, not a state. */
        public Ok {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * The operation was refused.
     *
     * @param error the typed reason, never null
     * @param <T> the value type
     * @param <E> the error type
     */
    record Err<T, E>(E error) implements Result<T, E> {
        /** Rejects a null error: null is a defect, not a state. */
        public Err {
            Objects.requireNonNull(error, "error");
        }
    }

    /**
     * Builds a success.
     *
     * @param value the value
     * @param <T> the value type
     * @param <E> the error type
     * @return the success
     */
    static <T, E> Result<T, E> ok(T value) {
        return new Ok<>(value);
    }

    /**
     * Builds a refusal.
     *
     * @param error the typed reason
     * @param <T> the value type
     * @param <E> the error type
     * @return the refusal
     */
    static <T, E> Result<T, E> err(E error) {
        return new Err<>(error);
    }

    /**
     * Transforms the value of a success and passes a refusal through unchanged.
     *
     * @param f the transformation
     * @param <U> the new value type
     * @return the transformed result
     */
    default <U> Result<U, E> map(Function<? super T, ? extends U> f) {
        return switch (this) {
            case Ok<T, E> ok -> ok(f.apply(ok.value()));
            case Err<T, E> err -> err(err.error());
        };
    }
}
