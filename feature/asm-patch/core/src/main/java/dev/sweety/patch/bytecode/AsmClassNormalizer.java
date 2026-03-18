package dev.sweety.patch.bytecode;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

public class AsmClassNormalizer implements ClassNormalizer {
    public byte[] normalize(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

            // Normalize by removing debug info and frames to ensure deterministic comparison
            // SKIP_DEBUG: Removes source file, line numbers, local variables, etc.
            // SKIP_FRAMES: Removes stack map frames (compiler dependent)
            reader.accept(writer, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            return writer.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to normalize class bytes using ASM", e);
        }
    }
}