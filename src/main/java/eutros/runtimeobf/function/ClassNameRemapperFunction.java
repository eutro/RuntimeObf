package eutros.runtimeobf.function;

@FunctionalInterface
public interface ClassNameRemapperFunction {
    String remapClassName(String className);
}
