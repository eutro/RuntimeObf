package eutros.runtimeobf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

/**
 * {@link Method Reflected methods} of {@link Bootstrap}, for convenience.
 */
public class BootstrapHelper {

    static {
        try {
            obfMethodOrFieldBootstrap = Bootstrap.class.getMethod("obfMethodOrFieldBootstrap",
                    MethodHandles.Lookup.class,
                    String.class,
                    MethodType.class,
                    int.class,
                    MethodHandle.class,
                    MethodHandle.class,
                    MethodHandle.class,
                    String[].class);
            obfTypeBootstrap = Bootstrap.class.getMethod("obfTypeBootstrap",
                    MethodHandles.Lookup.class,
                    String.class,
                    MethodType.class,
                    int.class,
                    MethodHandle.class,
                    MethodHandle.class,
                    String[].class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Three arguments are stacked automatically by the JVM:
     * <ul>
     * <li>A {@link MethodHandles.Lookup lookup} in the caller class.</li>
     * <li>The name indicated by the INVOKEDYNAMIC instruction.</li>
     * <li>The type indicated by the INVOKEDYNAMIC instruction.</li>
     * </ul>
     */
    public static final int JVM_STACKED = 3;

    /**
     * @see Bootstrap#obfMethodOrFieldBootstrap(MethodHandles.Lookup, String, MethodType, int, MethodHandle, MethodHandle, MethodHandle, String[])
     */
    public static final Method obfMethodOrFieldBootstrap;
    public static final int OMOFB_FIXED_ARGS = obfMethodOrFieldBootstrap.getParameterCount() - JVM_STACKED - 1;
    public static final int OMOFB_OPCODE = 0;
    public static final int OMOFB_GET_CLASS_REMAPPER = 1;
    public static final int OMOFB_GET_NAME_REMAPPER = 2;
    public static final int OMOFB_GET_ENV = 3;
    /**
     * @see Bootstrap#obfTypeBootstrap(MethodHandles.Lookup, String, MethodType, int, MethodHandle, MethodHandle, String[])
     */
    public static final Method obfTypeBootstrap;
    public static final int OTB_FIXED_ARGS = obfTypeBootstrap.getParameterCount() - JVM_STACKED - 1;
    public static final int OTB_OPCODE = 0;
    public static final int OTB_GET_CLASS_REMAPPER = 1;
    public static final int OTB_GET_ENV = 2;

}
