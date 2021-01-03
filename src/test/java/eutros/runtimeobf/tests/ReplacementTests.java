package eutros.runtimeobf.tests;

import eutros.runtimeobf.ReplacingMethodVisitor;
import eutros.runtimeobf.function.ClassNameRemapperFunction;
import eutros.runtimeobf.function.NameRemapperFunction;
import eutros.runtimeobf.function.TriFunction;
import eutros.runtimeobf.util.AsmHelper;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

public class ReplacementTests {

    private static ClassReader getClassReader(Class<?> clazz) {
        String resName = clazz.getName().replace('.', '/') + ".class";
        try (InputStream is = clazz.getClassLoader().getResourceAsStream(resName)) {
            assert is != null;
            return new ClassReader(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class Loader extends ClassLoader {
        public Class<?> define(Class<?> other, byte[] bytes) {
            return defineClass(other.getName(), bytes, 0, bytes.length);
        }
    }

    private static Class<?> tryTransform(Class<?> clazz) {
        ClassWriter cw = new ClassWriter(0);
        getClassReader(clazz).accept(new StubReplacingClassVisitor(cw), 0);
        byte[] bytes = cw.toByteArray();
        try {
            return forceVerify(new Loader().define(clazz, bytes));
        } catch (Error e) {
            String dumpLoc = System.getenv("eutros.runtimeobf.tests.dump_classes");
            if (dumpLoc != null) {
                Path path = new File(dumpLoc).toPath().resolve(clazz.getName().replace('.', '/') + ".class");
                try {
                    Files.createDirectories(path.getParent());
                    try (OutputStream os = Files.newOutputStream(path)) {
                        os.write(bytes);
                    }
                } catch (IOException ioe) {
                    throw new RuntimeException("Exception dumping", e);
                }

                throw new RuntimeException("Error with class, dumped to " + path, e);
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T tryConstructTransformed(Class<T> clazz) {
        try {
            return (T) tryTransform(clazz).getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static Class<?> forceVerify(Class<?> clazz) {
        clazz.getMethods();
        return clazz;
    }

    private static final Handle ZERO_HANDLE;
    private static final Handle IDENTITY_CLASSNAME_HANDLE;
    private static final Handle IDENTITY_NAME_HANDLE;

    public static final int ZERO = 0;
    public static final ClassNameRemapperFunction IDENTITY_CLASSNAME = className -> className;
    public static final NameRemapperFunction IDENTITY_NAME = (owner, name, descriptor) -> name;

    static {
        try {
            ZERO_HANDLE = AsmHelper.unreflectGetter(ReplacementTests.class.getField("ZERO"));
            IDENTITY_CLASSNAME_HANDLE = AsmHelper.unreflectGetter(ReplacementTests.class.getField("IDENTITY_CLASSNAME"));
            IDENTITY_NAME_HANDLE = AsmHelper.unreflectGetter(ReplacementTests.class.getField("IDENTITY_NAME"));
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final Map<String, String[]> classMappings = new HashMap<>();
    private static final Map<String, String[]> fieldNameMappings = new HashMap<>();
    private static final Map<String, String[]> methodNameMappings = new HashMap<>();

    private static final TriFunction<String, String, String, String> joinTabs = (s, s2, s3) ->
            new StringJoiner("\t").add(s).add(s2).add(s3).toString();

    static {
        classMappings.put("java/lang/String", new String[] { "java/lang/String" });
        fieldNameMappings.put("java/lang/Integer\tMAX_VALUE\tI", new String[] { "MAX_VALUE" });
        methodNameMappings.put("java/lang/Object\ttoString\t()Ljava/lang/String;", new String[] { "toString" });
    }

    private static class StubReplacingClassVisitor extends ClassVisitor {
        public StubReplacingClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new ReplacingMethodVisitor(mv, IDENTITY_CLASSNAME_HANDLE, IDENTITY_NAME_HANDLE, ZERO_HANDLE,
                    classMappings::containsKey, classMappings::get,
                    joinTabs.andThen(fieldNameMappings::containsKey), joinTabs.andThen(fieldNameMappings::get),
                    joinTabs.andThen(methodNameMappings::containsKey), joinTabs.andThen(methodNameMappings::get));
        }
    }

    @Test
    public void testTypes() {
        tryConstructTransformed(TypesTest.class);
    }

    @SuppressWarnings({ "unused", "ConstantConditions", "UnusedAssignment" })
    public static class TypesTest {
        public TypesTest() {
            String[] strings = new String[8];
            String[][] stringArrays = new String[8][];
            Object stringsObject = strings;
            boolean isStringsObjectStringArray = stringsObject instanceof String[];
            strings = (String[]) stringsObject;
        }
    }

    @Test
    public void testFrames() {
        tryConstructTransformed(FramesTest.class);
    }

    @SuppressWarnings({ "unused", "UnusedAssignment" })
    public static class FramesTest {
        volatile boolean F = false;

        public FramesTest() {
            String stringLocal;
            String[] stringsLocal = new String[] { "" };
            String[][][] strings3dLocal = new String[1][0][0];
            stringLocal = F ? (F ? stringsLocal : strings3dLocal[0][0])[0] : "";
        }
    }

}
