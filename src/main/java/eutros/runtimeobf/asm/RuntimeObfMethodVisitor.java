package eutros.runtimeobf.asm;

import eutros.runtimeobf.Bootstrap;
import eutros.runtimeobf.util.AsmHelper;
import eutros.runtimeobf.util.BootstrapHelper;
import eutros.runtimeobf.util.DescriptorHelper;
import org.objectweb.asm.*;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;

public class RuntimeObfMethodVisitor extends MethodVisitor {
    private static final Handle obfMethodOrFieldBootstrap = AsmHelper.unreflect(BootstrapHelper.obfMethodOrFieldBootstrap);
    private static final Handle obfTypeBootstrap = AsmHelper.unreflect(BootstrapHelper.obfTypeBootstrap);

    private final Handle getClassRemapper;
    private final Handle getNameRemapper;
    private final Handle getEnv;

    private final Predicate<String> internalNamePredicate;
    private final Function<String, String[]> expandInternalName;
    private final Predicate<OwnerNameAndDesc> fieldNamePredicate;
    private final Function<OwnerNameAndDesc, String[]> expandFieldName;
    private final Predicate<OwnerNameAndDesc> methodNamePredicate;
    private final Function<OwnerNameAndDesc, String[]> expandMethodName;

    private final Predicate<OwnerNameAndDesc> erasedFields;
    private final Predicate<OwnerNameAndDesc> erasedMethods;

    private boolean sawNew = false;

    /**
     * @param methodVisitor The method visitor to delegate to.
     * @param getClassRemapper The handle to use as the getClassRemapper argument in {@link Bootstrap} methods.
     * @param getNameRemapper The handle to use as the getNameRemapper argument in {@link Bootstrap} methods.
     * @param getEnv The handle to use as the getEnv argument in {@link Bootstrap} methods.
     * @param internalNamePredicate A predicate for internal names that need to be remapped and erased.
     * @param expandInternalName A function that yields the possible internal names a class may have at runtime.
     * @param fieldNamePredicate A predicate for field names to remap.
     * @param expandFieldName A function that yields the possible names a field may have at runtime.
     * @param methodNamePredicate A predicate for method names to remap.
     * @param expandMethodName A function that yields the possible names a method may have at runtime.
     * @param erasedFields A predicate for fields whose accesses should involve erasing types and nothing else.
     * @param erasedMethods A predicate for methods whose invocations should involve erasing types and nothing else.
     */
    public RuntimeObfMethodVisitor(MethodVisitor methodVisitor,
                                   Handle getClassRemapper,
                                   Handle getNameRemapper,
                                   Handle getEnv,
                                   Predicate<String> internalNamePredicate,
                                   Function<String, String[]> expandInternalName,
                                   Predicate<OwnerNameAndDesc> fieldNamePredicate,
                                   Function<OwnerNameAndDesc, String[]> expandFieldName,
                                   Predicate<OwnerNameAndDesc> methodNamePredicate,
                                   Function<OwnerNameAndDesc, String[]> expandMethodName,
                                   Predicate<OwnerNameAndDesc> erasedFields,
                                   Predicate<OwnerNameAndDesc> erasedMethods) {
        super(Opcodes.ASM9, methodVisitor);
        this.getClassRemapper = getClassRemapper;
        this.getNameRemapper = getNameRemapper;
        this.getEnv = getEnv;
        this.internalNamePredicate = internalNamePredicate;
        this.expandInternalName = expandInternalName;
        this.fieldNamePredicate = fieldNamePredicate;
        this.expandFieldName = expandFieldName;
        this.methodNamePredicate = methodNamePredicate;
        this.expandMethodName = expandMethodName;
        this.erasedFields = erasedFields;
        this.erasedMethods = erasedMethods;
    }

    public RuntimeObfMethodVisitor(MethodVisitor methodVisitor,
                                   Handle getClassRemapper,
                                   Handle getNameRemapper,
                                   Handle getEnv,
                                   Predicate<String> internalNamePredicate,
                                   Function<String, String[]> expandInternalName,
                                   Predicate<OwnerNameAndDesc> fieldNamePredicate,
                                   Function<OwnerNameAndDesc, String[]> expandFieldName,
                                   Predicate<OwnerNameAndDesc> methodNamePredicate,
                                   Function<OwnerNameAndDesc, String[]> expandMethodName) {
        this(methodVisitor,
                getClassRemapper,
                getNameRemapper,
                getEnv,
                internalNamePredicate,
                expandInternalName,
                fieldNamePredicate,
                expandFieldName,
                methodNamePredicate,
                expandMethodName,
                $ -> false,
                $ -> false);
    }

    protected boolean visitObfMethodOrFieldBootstrap(int opcode, String owner, String name, String desc) {
        boolean method = desc.charAt(0) == '(';
        String erasedDesc = DescriptorHelper.eraseDescriptorTypes(desc, internalNamePredicate);
        OwnerNameAndDesc ownerNameAndDesc = new OwnerNameAndDesc(owner, name, desc);

        if ((method ? erasedMethods : erasedFields).test(ownerNameAndDesc)) {
            if (method) {
                super.visitMethodInsn(opcode, owner, name, erasedDesc, opcode == Opcodes.INVOKEINTERFACE);
            } else {
                super.visitFieldInsn(opcode, owner, name, erasedDesc);
            }
            return true;
        }

        String[] owners;
        String[] names;
        String[] descs;

        if (internalNamePredicate.test(owner)) owners = expandInternalName.apply(owner);
        else owners = null;

        Predicate<OwnerNameAndDesc> namePredicate;
        Function<OwnerNameAndDesc, String[]> expandName;
        if (method) {
            namePredicate = methodNamePredicate;
            expandName = expandMethodName;
        } else {
            namePredicate = fieldNamePredicate;
            expandName = expandFieldName;
        }

        if (namePredicate.test(ownerNameAndDesc)) names = expandName.apply(ownerNameAndDesc);
        else names = null;

        descs = expandDescriptor(desc);

        int expectedLength =
                owners != null ? owners.length :
                        names != null ? names.length :
                                descs != null ? descs.length :
                                        -1;

        // no remapping done
        if (expectedLength == -1) return false;

        if (owners == null) {
            owners = new String[expectedLength];
            Arrays.fill(owners, owner);
        }

        if (names == null) {
            names = new String[expectedLength];
            Arrays.fill(names, name);
        }

        if (descs == null) {
            descs = new String[expectedLength];
            Arrays.fill(descs, desc);
        }

        Object[] args = new Object[BootstrapHelper.OMOFB_FIXED_ARGS + expectedLength * 3];
        args[BootstrapHelper.OMOFB_OPCODE] = opcode;
        args[BootstrapHelper.OMOFB_GET_CLASS_REMAPPER] = getClassRemapper;
        args[BootstrapHelper.OMOFB_GET_NAME_REMAPPER] = getNameRemapper;
        args[BootstrapHelper.OMOFB_GET_ENV] = getEnv;
        System.arraycopy(owners, 0, args, BootstrapHelper.OMOFB_FIXED_ARGS, expectedLength);
        System.arraycopy(names, 0, args, BootstrapHelper.OMOFB_FIXED_ARGS + expectedLength, expectedLength);
        System.arraycopy(descs, 0, args, BootstrapHelper.OMOFB_FIXED_ARGS + 2 * expectedLength, expectedLength);

        boolean constructor = "<init>".equals(name);
        if (constructor) {
            name = "construct";
            erasedDesc = erasedDesc.substring(0, erasedDesc.length() - 1) +
                    'L' + DescriptorHelper.eraseType(owner) + ';';
        }
        super.visitInvokeDynamicInsn(name, erasedDesc, obfMethodOrFieldBootstrap, args);
        return true;
    }

    protected void visitObfTypeBootstrap(String name, String desc, int opcode, String internalName) {
        String[] internalNamesMasked = expandInternalName.apply(DescriptorHelper.maskArray(internalName));
        Object[] args = new Object[BootstrapHelper.OTB_FIXED_ARGS + internalNamesMasked.length];
        args[BootstrapHelper.OTB_OPCODE] = opcode;
        args[BootstrapHelper.OTB_GET_CLASS_REMAPPER] = getClassRemapper;
        args[BootstrapHelper.OTB_GET_ENV] = getEnv;
        for (int i = 0; i < internalNamesMasked.length; i++) {
            args[i + BootstrapHelper.OTB_FIXED_ARGS] = DescriptorHelper.unmaskArray(internalName, internalNamesMasked[i]);
        }
        super.visitInvokeDynamicInsn(name, desc, obfTypeBootstrap, args);
    }

    private String[] expandDescriptor(String desc) {
        boolean expanded = false;
        StringBuffer[] bufs = new StringBuffer[]{new StringBuffer()};
        Matcher matcher = DescriptorHelper.DESCRIPTOR_NAME_PATTERN.matcher(desc);
        while (matcher.find()) {
            String internalName = matcher.group();
            if (internalNamePredicate.test(internalName)) {
                expanded = true;
                String[] expandedInternal = expandInternalName.apply(internalName);
                if (bufs.length < expandedInternal.length) {
                    StringBuffer[] newBufs = Arrays.copyOf(bufs, expandedInternal.length);
                    for (int i = bufs.length - 1; i < newBufs.length; i++) {
                        newBufs[i] = new StringBuffer(desc.subSequence(0, matcher.start()));
                    }
                    bufs = newBufs;
                }
                for (int i = 0; i < expandedInternal.length; i++) {
                    matcher.appendReplacement(bufs[i], Matcher.quoteReplacement(expandedInternal[i]));
                }
            } else {
                for (StringBuffer buf : bufs) {
                    matcher.appendReplacement(buf, Matcher.quoteReplacement(internalName));
                }
            }
        }
        if (!expanded) return null;
        for (StringBuffer buf : bufs) matcher.appendTail(buf);
        String[] ret = new String[bufs.length];
        for (int i = 0; i < ret.length; i++) ret[i] = bufs[i].toString();
        return ret;
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        if (!internalNamePredicate.test(DescriptorHelper.maskArray(type))) super.visitTypeInsn(opcode, type);
        String desc;
        String name;
        switch (opcode) {
            case Opcodes.NEW:
                sawNew = true; // ignore the next DUP insn
                return;
            case Opcodes.ANEWARRAY:
                desc = "(I)" + DescriptorHelper.repeatChar('[', DescriptorHelper.arrayDimensions(type) + 1) + "Ljava/lang/Object;";
                name = "newArray";
                break;
            case Opcodes.CHECKCAST:
                desc = "(Ljava/lang/Object;)" + DescriptorHelper.eraseType(type);
                name = "checkCast";
                break;
            case Opcodes.INSTANCEOF:
                desc = "(Ljava/lang/Object;)Z";
                name = "isInstance";
                break;

            default:
                throw new IllegalArgumentException();
        }
        visitObfTypeBootstrap(name, desc, opcode, type);
    }

    @Override
    public void visitInsn(int opcode) {
        if (!sawNew || opcode != Opcodes.DUP) {
            super.visitInsn(opcode);
        }
        sawNew = false;
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        if (visitObfMethodOrFieldBootstrap(opcode, owner, name, descriptor)) return;
        super.visitFieldInsn(opcode, owner, name, descriptor);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (visitObfMethodOrFieldBootstrap(opcode, owner, name, descriptor)) return;
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    @Override
    public void visitLdcInsn(Object value) {
        if (!(value instanceof Type)) {
            super.visitLdcInsn(value);
            return;
        }
        Type type = (Type) value;
        if ((type.getSort() != Type.OBJECT && type.getSort() != Type.ARRAY) ||
                !internalNamePredicate.test(type.toString())) {
            super.visitLdcInsn(value);
            return;
        }
        visitObfTypeBootstrap("constant", "()Ljava/lang/Class;", Opcodes.LDC, type.getInternalName());
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        String masked = DescriptorHelper.maskArray(descriptor);
        if (!internalNamePredicate.test(masked)) {
            super.visitMultiANewArrayInsn(descriptor, numDimensions);
            return;
        }
        String desc = "(" + DescriptorHelper.repeatChar('I', numDimensions) + ")" + DescriptorHelper.eraseDescriptorTypes(descriptor, internalNamePredicate);
        visitObfTypeBootstrap("multiNewArray", desc, Opcodes.MULTIANEWARRAY, masked);
    }

    @Override
    public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
        super.visitLocalVariable(name, DescriptorHelper.eraseDescriptorTypes(descriptor, internalNamePredicate), signature, start, end, index);
    }

    @Override
    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
        super.visitFrame(type, numLocal, transformLocals(local), numStack, transformLocals(stack));
    }

    private Object[] transformLocals(Object[] local) {
        Object[] ret = new Object[local.length];
        for (int i = 0; i < local.length; i++) {
            Object o = local[i];
            if (o instanceof String && internalNamePredicate.test(DescriptorHelper.maskArray(((String) o)))) {
                ret[i] = DescriptorHelper.eraseType((String) o);
            } else {
                ret[i] = o;
            }
        }
        return ret;
    }
}
