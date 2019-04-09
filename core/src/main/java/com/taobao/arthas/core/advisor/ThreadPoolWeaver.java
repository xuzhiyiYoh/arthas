package com.taobao.arthas.core.advisor;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.commons.Method;

import java.util.Set;

/**
 * Date: 2019/4/9
 *
 * @author xuzhiyi
 */
public class ThreadPoolWeaver extends ClassVisitor implements Opcodes {

    public ThreadPoolWeaver(ClassVisitor classVisitor) {
        super(Opcodes.ASM7, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {

        final MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

        if (!name.equals("execute")) {
            return mv;
        }

        return new AdviceAdapter(Opcodes.ASM7, new JSRInlinerAdapter(mv, access, name, descriptor, signature, exceptions), access, name, descriptor) {
            private final Type ASM_TYPE_HOOKS = Type.getType("Ljava/arthas/Hooks;");
            private final Type ASM_TYPE_SET = Type.getType(Set.class);

            @Override
            protected void onMethodEnter() {
                getStatic(ASM_TYPE_HOOKS, "THREAD_POOLS", ASM_TYPE_SET);
                loadThis();
                invokeInterface(ASM_TYPE_SET, Method.getMethod("boolean add(Object)"));
                pop();
            }
        };
    }
}
