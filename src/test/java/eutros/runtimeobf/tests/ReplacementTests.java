package eutros.runtimeobf.tests;

import eutros.runtimeobf.asm.OwnerNameAndDesc;
import eutros.runtimeobf.asm.RuntimeObfMethodVisitor;
import eutros.runtimeobf.function.ClassNameRemapperFunction;
import eutros.runtimeobf.function.NameRemapperFunction;
import eutros.runtimeobf.util.AsmHelper;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.Map;

public class ReplacementTests {

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
    private static final Map<OwnerNameAndDesc, String[]> fieldNameMappings = new HashMap<>();
    private static final Map<OwnerNameAndDesc, String[]> methodNameMappings = new HashMap<>();

    static {
        classMappings.put("java/lang/String", new String[] { "java/lang/String" });
        fieldNameMappings.put(new OwnerNameAndDesc("java/lang/Integer", "MAX_VALUE", "I"), new String[] { "MAX_VALUE" });
        methodNameMappings.put(new OwnerNameAndDesc("java/lang/Object", "toString", "()Ljava/lang/String;"), new String[] { "toString" });
    }

    private static class StubReplacingClassVisitor extends ClassVisitor {
        public StubReplacingClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new RuntimeObfMethodVisitor(mv, IDENTITY_CLASSNAME_HANDLE, IDENTITY_NAME_HANDLE, ZERO_HANDLE,
                    classMappings::containsKey, classMappings::get,
                    fieldNameMappings::containsKey, fieldNameMappings::get,
                    methodNameMappings::containsKey, methodNameMappings::get);
        }
    }

    @Test
    public void testTypes() {
        TestHelper.tryConstructTransformed(TypesTest.class, StubReplacingClassVisitor::new);
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
        TestHelper.tryConstructTransformed(FramesTest.class, StubReplacingClassVisitor::new);
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
