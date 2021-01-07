package eutros.runtimeobf.asm;

import eutros.runtimeobf.util.DescriptorHelper;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A class visitor that erases field types and method types.
 *
 * Note that class transformations should be done in two passes, running an {@link ErasingClassVisitor} first
 * to collect sets of erased fields and methods, and remapping after.
 */
public class ErasingClassVisitor extends ClassVisitor {
    private final Predicate<String> internalNamePredicate;
    private String internalName;

    private final Consumer<OwnerNameAndDesc> erasedFields;
    private final Consumer<OwnerNameAndDesc> erasedMethods;

    /**
     * @param classVisitor The class visitor to delegate to.
     * @param internalNamePredicate A predicate for internal names that need to be erased.
     * @param erasedFields A consumer of fields to call with the fields whose types are erased.
     * @param erasedMethods A consumer of methods to call with the methods whose types are erased.
     */
    public ErasingClassVisitor(ClassVisitor classVisitor,
                               Predicate<String> internalNamePredicate,
                               Consumer<OwnerNameAndDesc> erasedFields,
                               Consumer<OwnerNameAndDesc> erasedMethods) {
        super(Opcodes.ASM9, classVisitor);
        this.internalNamePredicate = internalNamePredicate;
        this.erasedFields = erasedFields;
        this.erasedMethods = erasedMethods;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if (internalNamePredicate.test(superName)) {
            throw new ImpossibleTransformationException("super", superName);
        }
        for (String interfaceName : interfaces) {
            if (internalNamePredicate.test(interfaceName)) {
                throw new ImpossibleTransformationException("interface", interfaceName);
            }
        }
        internalName = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        String erased = DescriptorHelper.eraseDescriptorTypes(descriptor, internalNamePredicate);
        if (!erased.equals(descriptor)) erasedFields.accept(new OwnerNameAndDesc(internalName, name, descriptor));
        return super.visitField(access, name, erased, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        String erased = DescriptorHelper.eraseDescriptorTypes(descriptor, internalNamePredicate);
        if (!erased.equals(descriptor)) erasedMethods.accept(new OwnerNameAndDesc(internalName, name, descriptor));
        return super.visitMethod(access, name, erased, signature, exceptions);
    }

    public static class ImpossibleTransformationException extends RuntimeException {
        public ImpossibleTransformationException(String location, String internalName) {
            super(String.format("Illegal %s name: %s. Changing class hierarchy is not possible.", location, internalName));
        }
    }
}
