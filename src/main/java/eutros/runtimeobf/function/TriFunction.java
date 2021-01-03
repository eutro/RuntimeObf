package eutros.runtimeobf.function;

import java.util.function.Function;
import java.util.function.Predicate;

@FunctionalInterface
public interface TriFunction<A, B, C, R> {
    R apply(A a, B b, C c);

    default <NR> TriFunction<A, B, C, NR> andThen(Function<R, NR> after) {
        return (a, b, c) -> after.apply(apply(a, b, c));
    }

    default TriPredicate<A, B, C> andThen(Predicate<R> after) {
        return (a, b, c) -> after.test(apply(a, b, c));
    }
}
