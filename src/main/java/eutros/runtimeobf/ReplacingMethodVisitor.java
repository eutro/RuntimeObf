package eutros.runtimeobf;

import eutros.runtimeobf.function.TriFunction;
import eutros.runtimeobf.function.TriPredicate;
import eutros.runtimeobf.util.AsmHelper;
import eutros.runtimeobf.util.DescriptorHelper;
import org.objectweb.asm.*;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;

public class ReplacingMethodVisitor extends MethodVisitor {
    private static final Handle obfMethodOrFieldBootstrap = AsmHelper.unreflect(BootstrapHelper.obfMethodOrFieldBootstrap);
    private static final Handle obfTypeBootstrap = AsmHelper.unreflect(BootstrapHelper.obfTypeBootstrap);

    private final Handle getClassRemapper;
    private final Handle getNameRemapper;
    private final Handle getEnv;

    private final Predicate<String> internalNamePredicate;
    private final Function<String, String[]> expandInternalName;
    private final TriPredicate<String, String, String> fieldNamePredicate;
    private final TriFunction<String, String, String, String[]> expandFieldName;
    private final TriPredicate<String, String, String> methodNamePredicate;
    private final TriFunction<String, String, String, String[]> expandMethodName;

    public ReplacingMethodVisitor(MethodVisitor methodVisitor,
                                  Handle getClassRemapper,
                                  Handle getNameRemapper,
                                  Handle getEnv,
                                  Predicate<String> internalNamePredicate,
                                  Function<String, String[]> expandInternalName,
                                  TriPredicate<String, String, String> fieldNamePredicate,
                                  TriFunction<String, String, String, String[]> expandFieldName,
                                  TriPredicate<String, String, String> methodNamePredicate,
                                  TriFunction<String, String, String, String[]> expandMethodName) {
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
    }

    protected boolean visitObfMethodOrFieldBootstrap(int opcode, String owner, String name, String desc) {
        String[] owners;
        String[] names;
        String[] descs;

        if (internalNamePredicate.test(owner)) owners = expandInternalName.apply(owner);
        else owners = null;

        boolean method = desc.charAt(0) == '(';
        TriPredicate<String, String, String> namePredicate;
        TriFunction<String, String, String, String[]> expandName;
        if (method) {
            namePredicate = methodNamePredicate;
            expandName = expandMethodName;
        } else {
            namePredicate = fieldNamePredicate;
            expandName = expandFieldName;
        }

        if (namePredicate.test(owner, name, desc)) names = expandName.apply(owner, name, desc);
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

        super.visitInvokeDynamicInsn(name, DescriptorHelper.eraseDescriptorTypes(desc, internalNamePredicate), obfMethodOrFieldBootstrap, args);
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
        StringBuffer[] bufs = new StringBuffer[] { new StringBuffer() };
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
                return; // <init>s are replaced already
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
