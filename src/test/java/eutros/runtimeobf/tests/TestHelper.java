package eutros.runtimeobf.tests;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

public class TestHelper {

    public static ClassReader getClassReader(Class<?> clazz) {
        String resName = clazz.getName().replace('.', '/') + ".class";
        try (InputStream is = clazz.getClassLoader().getResourceAsStream(resName)) {
            assert is != null;
            return new ClassReader(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class Loader extends ClassLoader {
        public Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    public static Class<?> tryLoad(String name, byte[] bytes) {
        try {
            return forceVerify(new Loader().define(name, bytes));
        } catch (Error e) {
            String dumpLoc = System.getenv("eutros.runtimeobf.tests.dump_classes");
            if (dumpLoc != null) {
                Path path = new File(dumpLoc).toPath().resolve(name.replace('.', '/') + ".class");
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
    public static <T> T tryConstructTransformed(Class<T> clazz, Function<ClassVisitor, ClassVisitor> makeVisitor) {
        try {
            ClassWriter cw = new ClassWriter(0);
            getClassReader(clazz).accept(makeVisitor.apply(cw), 0);
            byte[] bytes = cw.toByteArray();
            return (T) tryLoad(clazz.getName(), bytes).getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static Class<?> forceVerify(Class<?> clazz) {
        clazz.getMethods();
        return clazz;
    }

}
