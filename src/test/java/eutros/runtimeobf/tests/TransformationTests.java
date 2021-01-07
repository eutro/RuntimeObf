package eutros.runtimeobf.tests;

import eutros.runtimeobf.asm.ErasingClassVisitor;
import eutros.runtimeobf.asm.OwnerNameAndDesc;
import eutros.runtimeobf.asm.RuntimeObfMethodVisitor;
import eutros.runtimeobf.function.ClassNameRemapperFunction;
import eutros.runtimeobf.function.NameRemapperFunction;
import eutros.runtimeobf.util.AsmHelper;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class TransformationTests {

    private static final Handle getClassRemapper;
    private static final Handle getNameRemapper;
    private static final Handle getEnv;

    static {
        try {
            getClassRemapper = AsmHelper.unreflectGetter(TransformationTests.class.getField("CLASS_REMAPPER"));
            getNameRemapper = AsmHelper.unreflectGetter(TransformationTests.class.getField("NAME_REMAPPER"));
            getEnv = AsmHelper.unreflectGetter(TransformationTests.class.getField("ENV"));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException();
        }
    }

    public static int ENV;
    public static ClassNameRemapperFunction CLASS_REMAPPER = internalName -> "java/util/" + internalName;
    public static NameRemapperFunction NAME_REMAPPER = (owner, name, descriptor) -> name;

    @Test
    public void testTransformationListImpl() throws Throwable {
        ClassWriter cw = new ClassWriter(0);
        Predicate<String> internalNamePredicate = "java/util/ArrayList"::equals;
        Predicate<OwnerNameAndDesc> namePredicate = ownerNameAndDesc ->
                ownerNameAndDesc.owner.endsWith("TransformationListImplTest") &&
                        ownerNameAndDesc.desc.contains("ArrayList");
        Consumer<OwnerNameAndDesc> NOOP = $ -> {
        };
        TestHelper.getClassReader(TransformationListImplTest.class).accept(new ErasingClassVisitor(cw, internalNamePredicate, NOOP, NOOP) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new RuntimeObfMethodVisitor(mv,
                        getClassRemapper, getNameRemapper, getEnv,
                        internalNamePredicate, s -> new String[]{"ArrayList", "LinkedList"},
                        $ -> false, $ -> null,
                        $ -> false, $ -> null,
                        namePredicate, namePredicate);
            }
        }, 0);
        byte[] bytes = cw.toByteArray();
        ENV = 0;
        assert ((ListSupplier) TestHelper.tryLoad(TransformationListImplTest.class.getName(), bytes)
                .getConstructor()
                .newInstance())
                .getList() instanceof ArrayList;
        ENV = 1;
        assert ((ListSupplier) TestHelper.tryLoad(TransformationListImplTest.class.getName(), bytes)
                .getConstructor()
                .newInstance())
                .getList() instanceof LinkedList;
    }

    public interface ListSupplier {
        List<Object> getList();
    }

    public static class TransformationListImplTest implements ListSupplier {
        private ArrayList<Object> listField = createList();

        private ArrayList<Object> createList() {
            return new ArrayList<>();
        }

        @Override
        public List<Object> getList() {
            return listField;
        }
    }

}
