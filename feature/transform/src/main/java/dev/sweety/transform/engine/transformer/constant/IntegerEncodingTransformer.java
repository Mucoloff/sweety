package dev.sweety.transform.engine.transformer.constant;

import dev.sweety.transform.engine.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.util.Random;

/**
 * Integer Constant Obfuscation via Arithmetic Encoding.
 *
 * Replaces {@code LDC} integer constants and short-form integer pushes with
 * equivalent arithmetic expressions.  Three encoding schemes, chosen randomly:
 *
 * <ol>
 *   <li><strong>Additive split</strong>: {@code C → (A + (C - A))} where A is random</li>
 *   <li><strong>XOR pair</strong>:       {@code C → (K ^ (C ^ K))} where K is random</li>
 *   <li><strong>Multiply-add</strong>:   {@code C → (M * D + R)} where {@code M*D+R == C}</li>
 * </ol>
 *
 * Only applied when {@code @Transform(integers = true)} to avoid polluting tight
 * numeric loops with unnecessary arithmetic.
 *
 * Runtime cost: 2-3 extra integer operations per constant (JIT constant-folds
 * these within microseconds on a warm method).
 */
public final class IntegerEncodingTransformer extends Transformer {

    private static final Random RNG = new Random(0xEC51AC);

    @Override public String name() { return "IntegerEncoding"; }

    @Override
    public void transform(TransformContext ctx) {
        for (MethodNode mn : ctx.classNode().methods) {
            if (!MethodSelector.isEligible(mn)) continue;
            if (!MethodSelector.shouldTransform(ctx, mn)) continue;
            if (!MethodSelector.transformIntegers(ctx.classNode(), mn)) continue;

            encodeIntegers(mn);
        }
    }

    private void encodeIntegers(MethodNode mn) {
        for (AbstractInsnNode insn : mn.instructions.toArray()) {
            final int value = extractIntConstant(insn);
            if (value == Integer.MIN_VALUE) continue; // sentinel = "not an int constant"

            final InsnList encoded = encode(value);
            mn.instructions.insertBefore(insn, encoded);
            mn.instructions.remove(insn);
        }
    }

    // ── Encoding ──────────────────────────────────────────────────────────────

    private static InsnList encode(int c) {
        int scheme = RNG.nextInt(3);
        return switch (scheme) {
            case 0 -> additiveScheme(c);
            case 1 -> xorScheme(c);
            default -> multiplyAddScheme(c);
        };
    }

    /** C → A + (C - A)  where A is a random int */
    private static InsnList additiveScheme(int c) {
        int a = RNG.nextInt();
        int b = c - a; // b = C - A, so A + b = C
        InsnList list = new InsnList();
        list.add(pushInt(a));
        list.add(pushInt(b));
        list.add(new InsnNode(Opcodes.IADD));
        return list;
    }

    /** C → K ^ (C ^ K)  — XOR is its own inverse */
    private static InsnList xorScheme(int c) {
        int k = RNG.nextInt();
        int v = c ^ k;
        InsnList list = new InsnList();
        list.add(pushInt(k));
        list.add(pushInt(v));
        list.add(new InsnNode(Opcodes.IXOR));
        return list;
    }

    /**
     * C → (M * D) + R  — find M, D, R such that M*D + R == C.
     * Use small M to keep constants in integer range.
     */
    private static InsnList multiplyAddScheme(int c) {
        int m = (RNG.nextInt(254) + 2); // 2..255
        int d = c / m;
        int r = c - m * d;
        InsnList list = new InsnList();
        list.add(pushInt(m));
        list.add(pushInt(d));
        list.add(new InsnNode(Opcodes.IMUL));
        list.add(pushInt(r));
        list.add(new InsnNode(Opcodes.IADD));
        return list;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Push any int via the most compact opcode. */
    static AbstractInsnNode pushInt(int v) {
        return switch (v) {
            case -1 -> new InsnNode(Opcodes.ICONST_M1);
            case  0 -> new InsnNode(Opcodes.ICONST_0);
            case  1 -> new InsnNode(Opcodes.ICONST_1);
            case  2 -> new InsnNode(Opcodes.ICONST_2);
            case  3 -> new InsnNode(Opcodes.ICONST_3);
            case  4 -> new InsnNode(Opcodes.ICONST_4);
            case  5 -> new InsnNode(Opcodes.ICONST_5);
            default -> {
                if (v >= Byte.MIN_VALUE  && v <= Byte.MAX_VALUE)  yield new IntInsnNode(Opcodes.BIPUSH, v);
                if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE) yield new IntInsnNode(Opcodes.SIPUSH, v);
                yield new LdcInsnNode(v);
            }
        };
    }

    /**
     * Extracts the integer value of a constant-push instruction.
     * Returns {@link Integer#MIN_VALUE} if the instruction is not an integer constant.
     */
    private static int extractIntConstant(AbstractInsnNode insn) {
        return switch (insn.getOpcode()) {
            case Opcodes.ICONST_M1 -> -1;
            case Opcodes.ICONST_0  ->  0;
            case Opcodes.ICONST_1  ->  1;
            case Opcodes.ICONST_2  ->  2;
            case Opcodes.ICONST_3  ->  3;
            case Opcodes.ICONST_4  ->  4;
            case Opcodes.ICONST_5  ->  5;
            case Opcodes.BIPUSH, Opcodes.SIPUSH -> ((IntInsnNode) insn).operand;
            case Opcodes.LDC -> {
                Object cst = ((LdcInsnNode) insn).cst;
                yield cst instanceof Integer i ? i : Integer.MIN_VALUE;
            }
            default -> Integer.MIN_VALUE;
        };
    }
}
