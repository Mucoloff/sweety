package dev.sweety.patch.generator.pass;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
import dev.sweety.patch.ClassPatch;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-pass bytecode comparator for a pair of methods.
 */
public final class MethodDiffPass {

    private MethodDiffPass() {}

    public static void apply(ClassPatch classPatch, MethodNode origMethod, MethodNode modMethod) {
        List<String> origInsns = describeInstructions(origMethod.instructions);
        List<String> modInsns = describeInstructions(modMethod.instructions);

        Patch<String> diff = DiffUtils.diff(origInsns, modInsns);
        if (diff.getDeltas().isEmpty()) {
            return;
        }

        for (AbstractDelta<String> delta : diff.getDeltas()) {
            if (HeadInjectionPass.apply(classPatch, modMethod, delta)) {
                continue;
            }
            TailInjectionPass.apply(classPatch, origMethod, modMethod, delta);
        }
    }

    private static List<String> describeInstructions(InsnList insns) {
        List<String> list = new ArrayList<>(insns.size());
        for (AbstractInsnNode insn = insns.getFirst(); insn != null; insn = insn.getNext()) {
            list.add(insn.getOpcode() + ":" + insn.getType());
        }
        return list;
    }
}
