package eutros.runtimeobf.function;

@FunctionalInterface
public interface NameRemapperFunction {
    String remapName(String owner, String name, String descriptor);
}
