import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.LocalVariablesSorter;
import org.objectweb.asm.util.TraceClassVisitor;

public class Main {
    
    /**
     * A method visitor that uses a LocalVariablesSorter to add one int local
     * and then print it out just before method return.
     * 
     * <p>If {@link MyMethodVisitor#applyHack} is set to {@code true}, then the
     * LocalVariablesSorter's {@code changed} state will be forcibly (through reflection)
     * set to {@code true}.
     * 
     * @since Jan 24, 2013
     * @author Patrick Mahoney <pat@polycrystal.org>
     *
     */
    public static class MyMethodVisitor extends MethodVisitor implements Opcodes {

        public static boolean applyHack = false;

        public LocalVariablesSorter lvs;
        
        private int myVar;
        
        private final Label start = new Label();
        
        private final Label end = new Label();

        public MyMethodVisitor(MethodVisitor mv) {
            super(ASM4, mv);
        }
        
        @Override
        public void visitCode() {
            if (applyHack) {
                try {
                    final Field field = LocalVariablesSorter.class.getDeclaredField("changed");
                    field.setAccessible(true);
                    field.setBoolean(lvs, true);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            
            mv.visitCode();
            mv.visitLabel(start);
            myVar = lvs.newLocal(Type.INT_TYPE);
            mv.visitLdcInsn(16);
            mv.visitVarInsn(ISTORE, myVar);
        }
        
        @Override
        public void visitInsn(int opcode) {
            if (opcode >= IRETURN && opcode <= RETURN) {
                // print out myVar
                mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                mv.visitVarInsn(ILOAD, myVar);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V");
                
                mv.visitLabel(end);
                mv.visitLocalVariable("myNewLocal", Type.INT_TYPE.getDescriptor(), null, start, end, myVar);
            }
            super.visitInsn(opcode);
        }
        
    }
    
    /**
     * Just sends all non-init method through a LocalVariablesSorter and MyMethodVisitor.
     */
    public static class MyClassVisitor extends ClassVisitor {

        public MyClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM4, cv);
        }
        
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            if (mv == null || "<init>".equals(name)) {
                return mv;
            }
            MyMethodVisitor myMv = new MyMethodVisitor(mv);
            myMv.lvs = new LocalVariablesSorter(access, desc, myMv);
            return myMv.lvs;
        }
        
    }
    
    public static class MyClassLoader extends ClassLoader {
        
        private void printTrace(byte[] classData) {
            final PrintWriter out = new PrintWriter(System.out);
            final TraceClassVisitor trace = new TraceClassVisitor(out);
            final ClassReader cr = new ClassReader(classData);
            cr.accept(trace, ClassReader.EXPAND_FRAMES);
        }
        
        private byte[] readResource(String name) throws IOException {
            final InputStream io = getResourceAsStream(name);
            if (io == null) {
                throw new FileNotFoundException(name);
            }
            try {
                return IOUtils.toByteArray(io);
            } finally {
                IOUtils.closeQuietly(io);
            }
        }
        
        private byte[] instrument(String name) throws IOException {
            System.out.println("instrumenting class " + name);

            final String classFile = name.replace(".", "/") + ".class";
            final byte[] classData = readResource(classFile);
            System.out.println("----- BEFORE ----- ");
            printTrace(classData);

            final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            final ClassReader cr = new ClassReader(classData);
            final ClassVisitor visitor = new MyClassVisitor(cw);
            cr.accept(visitor, ClassReader.EXPAND_FRAMES);

            final byte[] newClassData = cw.toByteArray();
            System.out.println("----- AFTER ----- ");
            printTrace(newClassData);

            System.out.println("writing instrumented version to " + classFile);
            FileUtils.writeByteArrayToFile(new File(classFile), newClassData);

            return newClassData;
        }
        
        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.startsWith("Example")) {
                try {
                    byte[] b = instrument(name);
                    return defineClass(name, b, 0, b.length);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                return super.loadClass(name);
            }
        }

    }
    
    public static void main(String[] args) {
        for (int i = 0; i < args.length; ++i) {
            if ("--help".equals(args[i]) || "-h".equals(args[i])) {
                System.out.println("usage: Main [--apply-hack]");
                System.out.println("  instruments an internal Example.class and then");
                System.out.println("  writes the instrumented version to Example.class");
                System.exit(0);
            } else if ("--apply-hack".equals(args[i])) {
                MyMethodVisitor.applyHack = true;
            } else {
                System.err.println("invalid arg " + args[i]);
                System.err.println("try --help");
            }
        }
        
        try {
            (new MyClassLoader()).loadClass("Example");
            System.out.println("LocalVariablesSorter changed hack: " + MyMethodVisitor.applyHack);
            System.out.println("loaded just fine into " + System.getProperty("java.version"));
            System.out.println("but now try (with java7) 'java Example'");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
