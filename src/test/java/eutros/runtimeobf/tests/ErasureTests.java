package eutros.runtimeobf.tests;

import eutros.runtimeobf.asm.ErasingClassVisitor;
import eutros.runtimeobf.asm.OwnerNameAndDesc;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class ErasureTests {

    @Test
    public void testFieldsAndMethods() {
        Set<OwnerNameAndDesc> transformed = new HashSet<>();
        TestHelper.tryConstructTransformed(FieldAndMethodTest.class, cv -> new ErasingClassVisitor(cv, "java/lang/String"::equals, transformed::add, transformed::add));
        assert transformed.size() == 2;
        assert transformed.contains(new OwnerNameAndDesc(Type.getInternalName(FieldAndMethodTest.class), "stringField", Type.getDescriptor(String.class)));
        assert transformed.contains(new OwnerNameAndDesc(Type.getInternalName(FieldAndMethodTest.class), "stringMethod", Type.getMethodDescriptor(Type.getType(String.class))));
    }

    @SuppressWarnings("unused")
    public static class FieldAndMethodTest {
        private String stringField;

        private String stringMethod() {
            return null;
        }

        public FieldAndMethodTest() {
        }
    }

    @Test
    public void testNoHierarchyChanges() {
        Consumer<OwnerNameAndDesc> NOOP = $ -> {
        };
        try {
            TestHelper.tryConstructTransformed(NoHierarchyChangeTest.class, cv -> new ErasingClassVisitor(cv, "java/util/HashMap"::equals, NOOP, NOOP));
            throw new AssertionError();
        } catch (ErasingClassVisitor.ImpossibleTransformationException ignored) {
        }
        try {
            TestHelper.tryConstructTransformed(NoHierarchyChangeTest.class, cv -> new ErasingClassVisitor(cv, "java/util/Map"::equals, NOOP, NOOP));
            throw new AssertionError();
        } catch (ErasingClassVisitor.ImpossibleTransformationException ignored) {
        }
    }

    public static class NoHierarchyChangeTest extends HashMap<Object, Object> implements Map<Object, Object> {
    }
}
