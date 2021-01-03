package eutros.runtimeobf;

import eutros.runtimeobf.function.ClassNameRemapperFunction;
import eutros.runtimeobf.function.NameRemapperFunction;
import eutros.runtimeobf.util.DescriptorHelper;
import eutros.runtimeobf.util.RegexHelper;
import org.objectweb.asm.Opcodes;

import java.lang.invoke.*;
import java.lang.reflect.Array;
import java.util.regex.Matcher;

/**
 * Bootstrap methods for INVOKEDYNAMIC instructions.
 */
public class Bootstrap {

    /**
     * The INVOKEDYNAMIC bootstrap method that replaces method invocations and field accesses.
     * <p>
     * As a special case, constructor calls also come here, with the NEW instructions having been stripped.
     *
     * @param caller                    Stacked automatically by the JVM. A {@link MethodHandles.Lookup lookup} in the caller class.
     * @param invokedName               Stacked automatically by the JVM. The name indicated by the INVOKEDYNAMIC instruction.
     * @param invokedType               Stacked automatically by the JVM. The type indicated by the INVOKEDYNAMIC instruction.
     * @param opcode                    The opcode that the INVOKEDYNAMIC replaced. One of:
     *                                  INVOKEVIRTUAL,
     *                                  INVOKESPECIAL,
     *                                  INVOKESTATIC,
     *                                  INVOKEINTERFACE,
     *                                  GETSTATIC,
     *                                  PUTSTATIC,
     *                                  GETFIELD or
     *                                  PUTFIELD.
     * @param getClassRemapper          A method that returns a method handle that remaps class names.
     *                                  <p>
     *                                  ()L{@link ClassNameRemapperFunction eutros/runtimeobf/function/ClassNameRemapperFunction};
     * @param getNameRemapper           A method of that returns a function that remaps class names.
     *                                  Expected to have a descriptor:
     *                                  <p>
     *                                  ()L{@link NameRemapperFunction eutros/runtimeobf/function/NameRemapperFunction};
     * @param getEnv                    A method of that returns an integer representing the environment.
     *                                  Expected to have descriptor:
     *                                  <p>
     *                                  ()I
     *                                  <p>
     *                                  This will be used to index owners, names and descriptors to get which one to use.
     * @param ownersNamesAndDescriptors Three arrays of equal length flattened into one:
     *                                  An array of class names. {@code owners[getEnv()]} will be the one to use.
     *                                  An array of method names. {@code names[getEnv()]} will be the one to use.
     *                                  An array of method descriptors. {@code descriptors[getEnv()]} will be the one to use.
     * @return A {@link CallSite} for the INVOKEDYNAMIC instruction.
     * @throws Throwable if any of the method handles throw anything.
     */
    public static CallSite obfMethodOrFieldBootstrap(MethodHandles.Lookup caller,
                                                     @SuppressWarnings("unused")
                                                             String invokedName,
                                                     MethodType invokedType,
                                                     int opcode,
                                                     MethodHandle getClassRemapper,
                                                     MethodHandle getNameRemapper,
                                                     MethodHandle getEnv,
                                                     String... ownersNamesAndDescriptors)
            throws Throwable {

        assert ownersNamesAndDescriptors.length % 3 == 0;
        int maxEnv = ownersNamesAndDescriptors.length / 3;
        int env = (int) getEnv.invokeExact();
        assert env < maxEnv;
        String owner = ownersNamesAndDescriptors[env];
        String name = ownersNamesAndDescriptors[maxEnv + env];
        String descriptor = ownersNamesAndDescriptors[2 * maxEnv + env];

        ClassNameRemapperFunction classRemapper = (ClassNameRemapperFunction) getClassRemapper.invokeExact();
        NameRemapperFunction nameRemapper = (NameRemapperFunction) getNameRemapper.invokeExact();

        String mappedOwner = classRemapper.remapClassName(owner);
        String mappedName = nameRemapper.remapName(owner, name, descriptor);
        String mappedDescriptor = RegexHelper.replaceAll(DescriptorHelper.DESCRIPTOR_NAME_PATTERN.matcher(descriptor),
                matcher -> Matcher.quoteReplacement(classRemapper
                        .remapClassName(matcher
                                .group()
                                .replace('/', '.')))
                        .replace('.', '/'));

        ClassLoader loader = caller.lookupClass().getClassLoader();
        boolean methodCall = mappedDescriptor.charAt(0) == '(';

        Class<?> ownerClass = Class.forName(mappedOwner, false, loader);
        MethodType targetType = MethodType.fromMethodDescriptorString(methodCall ? mappedDescriptor : "()" + mappedDescriptor, loader);

        MethodHandle mh;
        if (methodCall) {
            switch (opcode) {
                case Opcodes.INVOKEVIRTUAL:
                case Opcodes.INVOKEINTERFACE:
                    mh = caller.findVirtual(ownerClass, mappedName, targetType);
                    break;
                case Opcodes.INVOKESPECIAL:
                    if ("<init>".equals(mappedName)) {
                        mh = caller.findConstructor(ownerClass, targetType);
                    } else {
                        mh = caller.findSpecial(ownerClass, mappedName, targetType, ownerClass);
                    }
                    break;
                case Opcodes.INVOKESTATIC:
                    mh = caller.findStatic(ownerClass, mappedName, targetType);
                    break;

                default:
                    throw new IllegalArgumentException();
            }
        } else {
            Class<?> fieldType = targetType.returnType();
            switch (opcode) {
                case Opcodes.GETSTATIC:
                    mh = caller.findStaticGetter(ownerClass, mappedName, fieldType);
                    break;
                case Opcodes.PUTSTATIC:
                    mh = caller.findStaticSetter(ownerClass, mappedName, fieldType);
                    break;
                case Opcodes.GETFIELD:
                    mh = caller.findGetter(ownerClass, mappedName, fieldType);
                    break;
                case Opcodes.PUTFIELD:
                    mh = caller.findSetter(ownerClass, mappedName, fieldType);
                    break;

                default:
                    throw new IllegalArgumentException();
            }
        }

        return new ConstantCallSite(mh.asType(invokedType));
    }

    /**
     * The INVOKEDYNAMIC bootstrap method that replaces single type instructions for classes.
     *
     * @param caller           Stacked automatically by the JVM. A {@link MethodHandles.Lookup lookup} in the caller class.
     * @param invokedName      Stacked automatically by the JVM. The name indicated by the INVOKEDYNAMIC instruction.
     * @param invokedType      Stacked automatically by the JVM. The type indicated by the INVOKEDYNAMIC instruction.
     * @param opcode           The opcode that the INVOKEDYNAMIC replaced. One of:
     *                         LDC,
     *                         CHECKCAST,
     *                         INSTANCEOF,
     *                         ANEWARRAY or
     *                         MULTIANEWARRAY.
     * @param getClassRemapper A method that returns a method handle that remaps class names.
     *                         <p>
     *                         ()L{@link ClassNameRemapperFunction eutros/runtimeobf/function/ClassNameRemapperFunction};
     * @param getEnv           A method of that returns an integer representing the environment.
     *                         Expected to have descriptor:
     *                         <p>
     *                         ()I
     * @param internalNames    An array of internal class names. {@code internalNames[getEnv()]} will be the one to use.
     * @return A {@link CallSite} for the INVOKEDYNAMIC instruction.
     * @throws Throwable if any of the method handles throw anything.
     */
    public static CallSite obfTypeBootstrap(MethodHandles.Lookup caller,
                                            @SuppressWarnings("unused")
                                                    String invokedName,
                                            MethodType invokedType,
                                            int opcode,
                                            MethodHandle getClassRemapper,
                                            MethodHandle getEnv,
                                            String... internalNames)
            throws Throwable {
        String internalName = internalNames[(int) getEnv.invokeExact()];
        ClassNameRemapperFunction classRemapper = (ClassNameRemapperFunction) getClassRemapper.invokeExact();
        String mappedName = classRemapper.remapClassName(internalName.replace('/', '.'));
        ClassLoader loader = caller.lookupClass().getClassLoader();
        Class<?> mappedClass = Class.forName(mappedName, false, loader);

        MethodHandle mh;
        switch (opcode) {
            case Opcodes.LDC:
                mh = MethodHandles.constant(Class.class, mappedClass);
                break;
            case Opcodes.CHECKCAST:
                mh = MethodHandles.identity(mappedClass);
                break;
            case Opcodes.INSTANCEOF:
                mh = CLASS_IS_INSTANCE.bindTo(mappedClass);
                break;
            case Opcodes.ANEWARRAY:
                mh = ARRAY_NEW_SINGLE.bindTo(mappedClass);
                break;
            case Opcodes.MULTIANEWARRAY:
                mh = ARRAY_NEW_MULTI.bindTo(mappedClass).asVarargsCollector(int[].class);
                break;
            default:
                throw new IllegalArgumentException();
        }

        return new ConstantCallSite(mh.asType(invokedType));
    }

    private static final MethodHandle ARRAY_NEW_SINGLE;
    private static final MethodHandle ARRAY_NEW_MULTI;
    private static final MethodHandle CLASS_IS_INSTANCE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            ARRAY_NEW_SINGLE = lookup.findStatic(Array.class, "newInstance",
                    MethodType.methodType(Object.class, Class.class, int.class));
            ARRAY_NEW_MULTI = lookup.findStatic(Array.class, "newInstance",
                    MethodType.methodType(Object.class, Class.class, int[].class));
            CLASS_IS_INSTANCE = lookup.findVirtual(Class.class, "isInstance",
                    MethodType.methodType(boolean.class, Object.class));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

}
