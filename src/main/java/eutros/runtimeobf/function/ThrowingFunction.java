package eutros.runtimeobf.function;

@FunctionalInterface
public interface ThrowingFunction<A, R, T extends Throwable> {
    R apply(A t) throws T;
}
