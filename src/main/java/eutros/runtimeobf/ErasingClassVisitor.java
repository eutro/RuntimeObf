package eutros.runtimeobf;

import eutros.runtimeobf.util.DescriptorHelper;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Arrays;
import java.util.function.Predicate;

public class ErasingClassVisitor extends ClassVisitor {
    private final Predicate<String> internalNamePredicate;

    public ErasingClassVisitor(ClassVisitor methodVisitor,
                               Predicate<String> internalNamePredicate) {
        super(Opcodes.ASM9, methodVisitor);
        this.internalNamePredicate = internalNamePredicate;
    }

    // there's no choice but to erase references to any of these
    // these will be reinserted when the class is initialized
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if (internalNamePredicate.test(superName)) superName = DescriptorHelper.eraseType(superName);
        interfaces = Arrays.stream(interfaces).filter(in -> !internalNamePredicate.test(in)).toArray(String[]::new);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        return super.visitField(access, name, DescriptorHelper.eraseDescriptorTypes(descriptor, internalNamePredicate), signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return super.visitMethod(access, name, DescriptorHelper.eraseDescriptorTypes(descriptor, internalNamePredicate), signature, exceptions);
    }
}
