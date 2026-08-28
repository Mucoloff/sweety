package dev.sweety.transform.vm;

/**
 * Operand-stack shuffling (POP/DUP/SWAP family). The VM's operand stack is one slot per JVM value
 * (not the real JVM's category-1/category-2 slot width), so {@code POP2}/{@code DUP2}/{@code DUP_X2}
 * can't tell at runtime whether the real bytecode meant "two category-1 values" or "one category-2
 * value" (long/double) — {@link dev.sweety.transform.engine.transformer.virtualize.VMCompiler} resolves
 * that ambiguity at compile time via frame analysis and emits the matching {@code _CAT1}/{@code _CAT2}
 * variant, so this executor never has to guess.
 *
 * <p>Every op here copies/moves {@link VMStack} slots WITHOUT reading them (via {@code primAt}/
 * {@code refAt}/{@code pushSlot}) — it never needs to know whether a slot holds a primitive or a
 * reference, only how many slots and in what order, so no PRIM/REF variants are needed here at all.
 */
final class StackOps {

    private StackOps() {}

    static void execute(VmOp op, VMStack stack) {
        switch (op) {
            case POP -> stack.popRaw(1);
            case POP2_CAT1 -> stack.popRaw(2);
            case POP2_CAT2 -> stack.popRaw(1); // one category-2 value occupies a single VM stack slot

            case DUP, DUP2_CAT2 -> stack.pushSlot(stack.primAt(0), stack.refAt(0)); // DUP2_CAT2: same as DUP (one cat-2 value)

            case DUP_X1, DUP_X2_CAT2 -> { // DUP_X2_CAT2: dup below one cat-2 value — same shape as DUP_X1
                long pt = stack.primAt(0); Object rt = stack.refAt(0);
                long pu = stack.primAt(1); Object ru = stack.refAt(1);
                stack.popRaw(2);
                stack.pushSlot(pt, rt);
                stack.pushSlot(pu, ru);
                stack.pushSlot(pt, rt);
            }

            case DUP2_CAT1 -> {
                long pt = stack.primAt(0); Object rt = stack.refAt(0);
                long p2 = stack.primAt(1); Object r2 = stack.refAt(1);
                stack.popRaw(2);
                stack.pushSlot(p2, r2);
                stack.pushSlot(pt, rt);
                stack.pushSlot(p2, r2);
                stack.pushSlot(pt, rt);
            }

            case DUP_X2_CAT1 -> {
                long pt = stack.primAt(0); Object rt = stack.refAt(0);
                long p2 = stack.primAt(1); Object r2 = stack.refAt(1);
                long p3 = stack.primAt(2); Object r3 = stack.refAt(2);
                stack.popRaw(3);
                stack.pushSlot(pt, rt);
                stack.pushSlot(p3, r3);
                stack.pushSlot(p2, r2);
                stack.pushSlot(pt, rt);
            }

            case SWAP -> {
                long pt = stack.primAt(0); Object rt = stack.refAt(0);
                long pu = stack.primAt(1); Object ru = stack.refAt(1);
                stack.popRaw(2);
                stack.pushSlot(pt, rt);
                stack.pushSlot(pu, ru);
            }

            default -> throw new IllegalStateException("Not a stack-manipulation op: " + op);
        }
    }
}
