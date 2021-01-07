package eutros.runtimeobf.util;

@FunctionalInterface
public interface ThrowingFunction<A, R, T extends Throwable> {
    R apply(A t) throws T;
}
