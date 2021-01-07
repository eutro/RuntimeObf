package eutros.runtimeobf.function;

import org.objectweb.asm.Type;

/**
 * Used in bootstrap methods to map class names.
 */
@FunctionalInterface
public interface ClassNameRemapperFunction {
    /**
     * Get the internal name of a class at runtime.
     *
     * @param internalName The unmapped {@link Type#getInternalName(Class) <b>internal</b> name} of the class to remap.
     * @return The mapped internal name of the class, as it is in this runtime.
     */
    String remapClassName(String internalName);
}
