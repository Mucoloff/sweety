package dev.sweety.patch.generator;

import dev.sweety.patch.ClassPatch;
import dev.sweety.patch.generator.pass.MethodDiffPass;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Generates compact differential ClassPatch models by comparing two versions of a class using ASM Tree and DiffUtils.
 */
public final class BytecodeDiffGenerator {

    private BytecodeDiffGenerator() {}

    public static ClassPatch generatePatch(byte[] originalClassBytes, byte[] modifiedClassBytes) {
        ClassNode originalNode = new ClassNode();
        ClassNode modifiedNode = new ClassNode();

        new ClassReader(originalClassBytes).accept(originalNode, ClassReader.EXPAND_FRAMES);
        new ClassReader(modifiedClassBytes).accept(modifiedNode, ClassReader.EXPAND_FRAMES);

        return generatePatch(originalNode, modifiedNode);
    }

    public static ClassPatch generatePatch(ClassNode originalNode, ClassNode modifiedNode) {
        ClassPatch classPatch = ClassPatch.of(originalNode.name);

        Map<String, MethodNode> originalMethods = new HashMap<>();
        for (MethodNode mn : originalNode.methods) {
            originalMethods.put(mn.name + mn.desc, mn);
        }

        for (MethodNode modMethod : modifiedNode.methods) {
            MethodNode origMethod = originalMethods.get(modMethod.name + modMethod.desc);
            if (origMethod == null) {
                // Completely new method injected
                continue;
            }

            MethodDiffPass.apply(classPatch, origMethod, modMethod);
        }

        return classPatch;
    }
}
