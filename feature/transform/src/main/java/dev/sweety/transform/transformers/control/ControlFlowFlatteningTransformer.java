package dev.sweety.transform.transformers.control;

import dev.sweety.transform.engine.MethodSelector;
import dev.sweety.transform.engine.TransformContext;
import dev.sweety.transform.engine.Transformer;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Control Flow Flattening (CFF) Transformer.
 *
 * Flattens method control flow into a state-machine dispatcher loop (while (state != END) switch(state)).
 * State transitions are computed via Mixed Boolean-Arithmetic (MBA) and opaque static seed predicates,
 * preventing static decompiler recovery (CFR, Fernflower, Procyon, JADX).
 */
public final class ControlFlowFlatteningTransformer extends Transformer {

    private static final Random RNG = new Random(
            Long.parseLong(System.getProperty("sweety.transform.seed", String.valueOf(0x5EED_CFFL))));

    @Override
    public String name() {
        return "ControlFlowFlattening";
    }

    @Override
    public void transform(final TransformContext ctx) {
        final ClassNode cn = ctx.classNode();
        if ((cn.access & Opcodes.ACC_INTERFACE) != 0) {
            return;
        }

        boolean anyFlattened = false;
        for (final MethodNode mn : cn.methods) {
            if (!MethodSelector.isEligible(mn)) continue;
            if (!MethodSelector.shouldTransform(ctx, mn)) continue;
            if ("<init>".equals(mn.name) || "<clinit>".equals(mn.name)) continue;
            if ((mn.access & Opcodes.ACC_SYNCHRONIZED) != 0) continue;
            if (mn.instructions.size() < 6 || mn.instructions.size() > 800) continue;
            // Skip methods with try-catch blocks to prevent StackMapTable desync
            if (mn.tryCatchBlocks != null && !mn.tryCatchBlocks.isEmpty()) continue;

            if (flattenMethod(cn, mn, ctx.frameClassLoader())) {
                anyFlattened = true;
            }
        }

        if (anyFlattened) {
            OpaqueSeed.ensureField(cn);
        }
    }

    private static class Block {
        final int id;
        final LabelNode entryLabel;
        final List<AbstractInsnNode> instructions = new ArrayList<>();

        Block(int id, LabelNode entryLabel) {
            this.id = id;
            this.entryLabel = entryLabel;
        }
    }

    private boolean flattenMethod(final ClassNode cn, final MethodNode mn, final ClassLoader frameClassLoader) {
        final InsnList backup = cloneInsnList(mn.instructions);
        final AbstractInsnNode[] array = mn.instructions.toArray();
        if (array.length < 6) return false;

        final Set<AbstractInsnNode> leaders = new HashSet<>();
        AbstractInsnNode firstReal = array[0];
        while (firstReal != null && (firstReal instanceof LabelNode || firstReal instanceof LineNumberNode || firstReal instanceof FrameNode)) {
            firstReal = firstReal.getNext();
        }
        if (firstReal == null) return false;
        leaders.add(firstReal);

        for (final AbstractInsnNode insn : array) {
            if (insn instanceof JumpInsnNode j) {
                if (j.label != null) {
                    AbstractInsnNode target = j.label;
                    while (target != null && (target instanceof LabelNode || target instanceof LineNumberNode || target instanceof FrameNode)) {
                        target = target.getNext();
                    }
                    if (target != null) leaders.add(target);
                }
                AbstractInsnNode next = insn.getNext();
                while (next != null && (next instanceof LabelNode || next instanceof LineNumberNode || next instanceof FrameNode)) {
                    next = next.getNext();
                }
                if (next != null) leaders.add(next);
            } else if (insn instanceof TableSwitchInsnNode ts) {
                for (final LabelNode l : ts.labels) {
                    AbstractInsnNode target = l;
                    while (target != null && (target instanceof LabelNode || target instanceof LineNumberNode || target instanceof FrameNode)) {
                        target = target.getNext();
                    }
                    if (target != null) leaders.add(target);
                }
                if (ts.dflt != null) {
                    AbstractInsnNode dfltTarget = ts.dflt;
                    while (dfltTarget != null && (dfltTarget instanceof LabelNode || dfltTarget instanceof LineNumberNode || dfltTarget instanceof FrameNode)) {
                        dfltTarget = dfltTarget.getNext();
                    }
                    if (dfltTarget != null) leaders.add(dfltTarget);
                }
            } else if (insn.getOpcode() >= Opcodes.IRETURN && insn.getOpcode() <= Opcodes.RETURN) {
                AbstractInsnNode next = insn.getNext();
                while (next != null && (next instanceof LabelNode || next instanceof LineNumberNode || next instanceof FrameNode)) {
                    next = next.getNext();
                }
                if (next != null) leaders.add(next);
            }
        }

        final List<Block> blocks = new ArrayList<>();
        Block currentBlock = null;
        int blockCounter = 1;

        for (final AbstractInsnNode insn : array) {
            if (insn instanceof LineNumberNode || insn instanceof FrameNode) continue;

            if (leaders.contains(insn)) {
                final LabelNode entry = (insn instanceof LabelNode ln) ? ln : new LabelNode();
                currentBlock = new Block(blockCounter++, entry);
                blocks.add(currentBlock);
            }

            if (currentBlock != null) {
                currentBlock.instructions.add(insn);
            }
        }

        if (blocks.size() < 3) return false;

        final Map<LabelNode, Block> labelToBlock = new HashMap<>();
        for (final Block b : blocks) {
            for (final AbstractInsnNode insn : b.instructions) {
                if (insn instanceof LabelNode ln) {
                    labelToBlock.put(ln, b);
                }
            }
            labelToBlock.put(b.entryLabel, b);
        }

        final int stateVar = mn.maxLocals;
        mn.maxLocals += 2;

        final int endState = 0x7FFFFFFF;
        final LabelNode loopHead = new LabelNode();
        final LabelNode loopEnd = new LabelNode();
        final LabelNode defaultLabel = new LabelNode();

        final List<Block> shuffledBlocks = new ArrayList<>(blocks);
        Collections.shuffle(shuffledBlocks, RNG);

        final int[] keys = new int[shuffledBlocks.size()];
        final LabelNode[] switchLabels = new LabelNode[shuffledBlocks.size()];
        final Map<Integer, Integer> blockIdToKey = new HashMap<>();

        for (int i = 0; i < shuffledBlocks.size(); i++) {
            final Block b = shuffledBlocks.get(i);
            final int key = 1000 + i * 37 + (RNG.nextInt(15) + 1);
            keys[i] = key;
            switchLabels[i] = b.entryLabel;
            blockIdToKey.put(b.id, key);
        }

        final int initialKey = blockIdToKey.get(blocks.get(0).id);
        final InsnList flattened = new InsnList();

        // 1. Initial State Assignment
        flattened.add(new LdcInsnNode(initialKey));
        flattened.add(new VarInsnNode(Opcodes.ISTORE, stateVar));
        flattened.add(new JumpInsnNode(Opcodes.GOTO, loopHead));

        // 2. Dispatcher Loop Head
        flattened.add(loopHead);
        flattened.add(new VarInsnNode(Opcodes.ILOAD, stateVar));
        flattened.add(new LdcInsnNode(endState));
        flattened.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, loopEnd));

        flattened.add(new VarInsnNode(Opcodes.ILOAD, stateVar));
        flattened.add(new LookupSwitchInsnNode(defaultLabel, keys, switchLabels));

        // 3. Emit Basic Blocks with State Transitions
        for (int i = 0; i < blocks.size(); i++) {
            final Block b = blocks.get(i);
            flattened.add(b.entryLabel);

            final List<AbstractInsnNode> insns = b.instructions;
            for (int j = 0; j < insns.size(); j++) {
                final AbstractInsnNode insn = insns.get(j);

                if (j == insns.size() - 1) {
                    if (insn instanceof JumpInsnNode jmp) {
                        if (jmp.getOpcode() == Opcodes.GOTO) {
                            final Block targetBlock = labelToBlock.get(jmp.label);
                            final int nextKey = targetBlock != null ? blockIdToKey.get(targetBlock.id) : endState;
                            emitMbaStateTransition(cn, flattened, stateVar, nextKey);
                            flattened.add(new JumpInsnNode(Opcodes.GOTO, loopHead));
                            continue;
                        } else {
                            // Conditional branch
                            final Block trueBlock = labelToBlock.get(jmp.label);
                            final Block falseBlock = (i + 1 < blocks.size()) ? blocks.get(i + 1) : null;

                            final int trueKey = trueBlock != null ? blockIdToKey.get(trueBlock.id) : endState;
                            final int falseKey = falseBlock != null ? blockIdToKey.get(falseBlock.id) : endState;

                            final LabelNode trueBranch = new LabelNode();
                            flattened.add(new JumpInsnNode(jmp.getOpcode(), trueBranch));

                            // False Path
                            emitMbaStateTransition(cn, flattened, stateVar, falseKey);
                            flattened.add(new JumpInsnNode(Opcodes.GOTO, loopHead));

                            // True Path
                            flattened.add(trueBranch);
                            emitMbaStateTransition(cn, flattened, stateVar, trueKey);
                            flattened.add(new JumpInsnNode(Opcodes.GOTO, loopHead));
                            continue;
                        }
                    } else if (insn.getOpcode() >= Opcodes.IRETURN && insn.getOpcode() <= Opcodes.RETURN || insn.getOpcode() == Opcodes.ATHROW) {
                        flattened.add(insn);
                        continue;
                    } else {
                        flattened.add(insn);
                        final Block nextBlock = (i + 1 < blocks.size()) ? blocks.get(i + 1) : null;
                        final int nextKey = nextBlock != null ? blockIdToKey.get(nextBlock.id) : endState;
                        emitMbaStateTransition(cn, flattened, stateVar, nextKey);
                        flattened.add(new JumpInsnNode(Opcodes.GOTO, loopHead));
                        continue;
                    }
                }

                if (!(insn instanceof LabelNode && labelToBlock.containsKey(insn))) {
                    flattened.add(insn);
                }
            }
        }

        // 4. Default Case & Loop End
        flattened.add(defaultLabel);
        flattened.add(new LdcInsnNode(endState));
        flattened.add(new VarInsnNode(Opcodes.ISTORE, stateVar));
        flattened.add(new JumpInsnNode(Opcodes.GOTO, loopHead));

        flattened.add(loopEnd);
        flattened.add(new InsnNode(Opcodes.RETURN));

        mn.instructions = flattened;

        // Verify transformed method bytecode soundness
        if (!verifySoundness(cn, mn, frameClassLoader)) {
            mn.instructions = backup;
            return false;
        }

        return true;
    }

    private static void emitMbaStateTransition(final ClassNode cn, final InsnList list, final int stateVar, final int targetKey) {
        final int seed = OpaqueSeed.seed(cn.name);
        final int diff = targetKey ^ seed;

        list.add(OpaqueSeed.get(cn));
        list.add(new LdcInsnNode(diff));
        list.add(new InsnNode(Opcodes.IXOR));
        list.add(new VarInsnNode(Opcodes.ISTORE, stateVar));
    }

    private static InsnList cloneInsnList(final InsnList original) {
        final InsnList clone = new InsnList();
        final Map<LabelNode, LabelNode> labelMap = new HashMap<>();
        for (final AbstractInsnNode insn : original.toArray()) {
            if (insn instanceof LabelNode ln) {
                labelMap.put(ln, new LabelNode());
            }
        }
        for (final AbstractInsnNode insn : original.toArray()) {
            clone.add(insn.clone(labelMap));
        }
        return clone;
    }

    private static boolean verifySoundness(final ClassNode cn, final MethodNode mn, final ClassLoader frameClassLoader) {
        try {
            final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
                @Override
                protected String getCommonSuperClass(final String type1, final String type2) {
                    final ClassLoader cl = (frameClassLoader != null) ? frameClassLoader : getClass().getClassLoader();
                    Class<?> c, d;
                    try {
                        c = Class.forName(type1.replace('/', '.'), false, cl);
                        d = Class.forName(type2.replace('/', '.'), false, cl);
                    } catch (final Exception e) {
                        return "java/lang/Object";
                    }
                    if (c.isAssignableFrom(d)) return type1;
                    if (d.isAssignableFrom(c)) return type2;
                    if (c.isInterface() || d.isInterface()) return "java/lang/Object";
                    do {
                        c = c.getSuperclass();
                    } while (!c.isAssignableFrom(d));
                    return c.getName().replace('.', '/');
                }
            };
            cn.accept(cw);
            return true;
        } catch (final Throwable t) {
            return false;
        }
    }
}
