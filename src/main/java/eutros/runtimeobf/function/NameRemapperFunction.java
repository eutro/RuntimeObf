package eutros.runtimeobf.function;

/**
 * Used in bootstrap methods for remapping fields and methods.
 */
@FunctionalInterface
public interface NameRemapperFunction {
    /**
     * Get the name of a field or method at runtime.
     *
     * @param owner The unmapped internal name of the owning class.
     * @param name The unmapped name of the field or method.
     * @param descriptor The descriptor of the field or method.
     * @return The mapped name of the field or method, as it is in this runtime.
     */
    String remapName(String owner, String name, String descriptor);
}
