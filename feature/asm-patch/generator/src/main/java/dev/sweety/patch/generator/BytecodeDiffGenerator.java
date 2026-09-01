package dev.sweety.patch.generator;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import dev.sweety.patch.ClassPatch;
import dev.sweety.patch.MethodPatch;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates compact differential ClassPatch models by comparing two versions of a class using ASM Tree and DiffUtils.
 */
public final class BytecodeDiffGenerator {

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

            List<String> origInsns = describeInstructions(origMethod.instructions);
            List<String> modInsns = describeInstructions(modMethod.instructions);

            Patch<String> diff = DiffUtils.diff(origInsns, modInsns);
            if (!diff.getDeltas().isEmpty()) {
                for (AbstractDelta<String> delta : diff.getDeltas()) {
                    if (delta.getType() == DeltaType.INSERT && delta.getTarget().getPosition() == 0) {
                        // Injection at HEAD
                        final InsnList injectedInsns = cloneInsnSublist(modMethod.instructions, 0, delta.getTarget().size());
                        classPatch.patchMethod(MethodPatch.atHead(modMethod.name, modMethod.desc, (mv, version) -> {
                            injectedInsns.accept(mv);
                        }));
                    }
                }
            }
        }

        return classPatch;
    }

    private static List<String> describeInstructions(InsnList insns) {
        List<String> list = new ArrayList<>(insns.size());
        for (AbstractInsnNode insn = insns.getFirst(); insn != null; insn = insn.getNext()) {
            list.add(insn.getOpcode() + ":" + insn.getType());
        }
        return list;
    }

    private static InsnList cloneInsnSublist(InsnList source, int start, int count) {
        InsnList list = new InsnList();
        int idx = 0;
        for (AbstractInsnNode insn = source.getFirst(); insn != null && idx < (start + count); insn = insn.getNext(), idx++) {
            if (idx >= start) {
                list.add(insn.clone(new HashMap<>()));
            }
        }
        return list;
    }
}
