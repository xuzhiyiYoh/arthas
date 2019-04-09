package com.taobao.arthas.core.advisor;

import com.taobao.arthas.core.GlobalOptions;
import com.taobao.arthas.core.util.FileUtils;
import com.taobao.arthas.core.util.LogUtil;
import com.taobao.arthas.core.util.affect.EnhancerAffect;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;

/**
 * Date: 2019/4/9
 *
 * @author xuzhiyi
 */
public class ThreadPoolEnhancer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        final ClassReader cr = new ClassReader(classfileBuffer);
        final ClassWriter cw = new ClassWriter(cr, COMPUTE_FRAMES | COMPUTE_MAXS);
        cr.accept(new ThreadPoolWeaver(cw), EXPAND_FRAMES);
        byte[] bytes = cw.toByteArray();
        dumpClassIfNecessary(className, bytes);
        return bytes;
    }

    /**
     * dump class to file
     */
    private static void dumpClassIfNecessary(String className, byte[] data) {
        final File dumpClassFile = new File("./arthas-class-dump/" + className + ".class");
        final File classPath = new File(dumpClassFile.getParent());

        // 创建类所在的包路径
        if (!classPath.mkdirs()
            && !classPath.exists()) {
            return;
        }

        // 将类字节码写入文件
        try {
            FileUtils.writeByteArrayToFile(dumpClassFile, data);
            LogUtil.getArthasLogger().warn("dump class path:" + dumpClassFile.getPath());
        } catch (IOException e) {
        }

    }
}
