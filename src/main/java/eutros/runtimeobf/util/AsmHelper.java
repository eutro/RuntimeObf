package eutros.runtimeobf.util;

import org.objectweb.asm.Type;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class AsmHelper {
    public static Handle unreflect(Method method) {
        return new Handle(methodModsToHandleType(method.getModifiers()),
                Type.getInternalName(method.getDeclaringClass()),
                method.getName(),
                Type.getMethodDescriptor(method),
                Modifier.isInterface(method.getModifiers()));
    }

    public static Handle unreflectGetter(Field field) {
        return new Handle(fieldModsToHandleType(field.getModifiers(), true),
                Type.getInternalName(field.getDeclaringClass()),
                field.getName(),
                Type.getDescriptor(field.getType()),
                false);
    }

    public static Handle unreflectSetter(Field field) {
        return new Handle(fieldModsToHandleType(field.getModifiers(), false),
                Type.getInternalName(field.getDeclaringClass()),
                field.getName(),
                Type.getDescriptor(field.getType()),
                false);
    }

    private static int fieldModsToHandleType(int modifiers, boolean getter) {
        if (Modifier.isStatic(modifiers)) return getter ? Opcodes.H_GETSTATIC : Opcodes.H_PUTSTATIC;
        return getter ? Opcodes.H_GETFIELD : Opcodes.H_PUTFIELD;
    }

    private static int methodModsToHandleType(int modifiers) {
        if (Modifier.isStatic(modifiers)) return Opcodes.H_INVOKESTATIC;
        if (Modifier.isInterface(modifiers)) return Opcodes.H_INVOKEINTERFACE;
        return Opcodes.H_INVOKEVIRTUAL;
    }
}
