package eutros.runtimeobf.function;

@FunctionalInterface
public interface InternalNameRemapperFunction {
    String remapInternalName(String internalName);
}
