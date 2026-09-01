package dev.sweety.transform.vm.ops;
import dev.sweety.transform.vm.core.VmOp;
import dev.sweety.transform.vm.core.VMSupport;
import dev.sweety.transform.vm.state.PendingNew;
import dev.sweety.transform.vm.state.VMLocals;
import dev.sweety.transform.vm.state.VMStack;

/** Int/long/float/double arithmetic, bitwise, compare and widening/narrowing conversion ops. Pure — no
 * reflection, no I/O, no boxing (reads/writes {@link VMStack}'s raw primitive array directly). */
public final class ArithmeticOps {

    private ArithmeticOps() {}

    public static void execute(VmOp op, VMStack stack) {
        switch (op) {
            case IADD  -> { int b = stack.popI(); int a = stack.popI(); stack.pushI(a + b); }
            case ISUB  -> { int b = stack.popI(); int a = stack.popI(); stack.pushI(a - b); }
            case IMUL  -> { int b = stack.popI(); int a = stack.popI(); stack.pushI(a * b); }
            case IDIV  -> { int b = stack.popI(); int a = stack.popI(); stack.pushI(a / b); }
            case IREM  -> { int b = stack.popI(); int a = stack.popI(); stack.pushI(a % b); }
            case INEG  -> stack.pushI(-stack.popI());
            case IAND  -> { int b = stack.popI(); int a = stack.popI(); stack.pushI(a & b); }
            case IOR   -> { int b = stack.popI(); int a = stack.popI(); stack.pushI(a | b); }
            case IXOR  -> { int b = stack.popI(); int a = stack.popI(); stack.pushI(a ^ b); }
            case ISHL  -> { int b = stack.popI(); int a = stack.popI(); stack.pushI(a << b); }
            case ISHR  -> { int b = stack.popI(); int a = stack.popI(); stack.pushI(a >> b); }
            case IUSHR -> { int b = stack.popI(); int a = stack.popI(); stack.pushI(a >>> b); }

            case LADD  -> { long b = stack.popL(); long a = stack.popL(); stack.pushL(a + b); }
            case LSUB  -> { long b = stack.popL(); long a = stack.popL(); stack.pushL(a - b); }
            case LMUL  -> { long b = stack.popL(); long a = stack.popL(); stack.pushL(a * b); }
            case LDIV  -> { long b = stack.popL(); long a = stack.popL(); stack.pushL(a / b); }
            case LREM  -> { long b = stack.popL(); long a = stack.popL(); stack.pushL(a % b); }
            case LNEG  -> stack.pushL(-stack.popL());
            case LAND  -> { long b = stack.popL(); long a = stack.popL(); stack.pushL(a & b); }
            case LOR   -> { long b = stack.popL(); long a = stack.popL(); stack.pushL(a | b); }
            case LXOR  -> { long b = stack.popL(); long a = stack.popL(); stack.pushL(a ^ b); }
            case LCMP  -> { long b = stack.popL(); long a = stack.popL(); stack.pushI(Long.compare(a, b)); }

            case FADD  -> { float b = stack.popF(); float a = stack.popF(); stack.pushF(a + b); }
            case FSUB  -> { float b = stack.popF(); float a = stack.popF(); stack.pushF(a - b); }
            case FMUL  -> { float b = stack.popF(); float a = stack.popF(); stack.pushF(a * b); }
            case FDIV  -> { float b = stack.popF(); float a = stack.popF(); stack.pushF(a / b); }
            case FREM  -> { float b = stack.popF(); float a = stack.popF(); stack.pushF(a % b); }
            case FNEG  -> stack.pushF(-stack.popF());
            case FCMPL -> { float b = stack.popF(); float a = stack.popF(); stack.pushI(Float.compare(a, b) < 0 ? -1 : Float.compare(a, b) == 0 ? 0 : 1); }
            case FCMPG -> { float b = stack.popF(); float a = stack.popF(); stack.pushI(Float.compare(a, b) > 0 ? 1 : Float.compare(a, b) == 0 ? 0 : -1); }

            case DADD  -> { double b = stack.popD(); double a = stack.popD(); stack.pushD(a + b); }
            case DSUB  -> { double b = stack.popD(); double a = stack.popD(); stack.pushD(a - b); }
            case DMUL  -> { double b = stack.popD(); double a = stack.popD(); stack.pushD(a * b); }
            case DDIV  -> { double b = stack.popD(); double a = stack.popD(); stack.pushD(a / b); }
            case DREM  -> { double b = stack.popD(); double a = stack.popD(); stack.pushD(a % b); }
            case DNEG  -> stack.pushD(-stack.popD());
            case DCMPL -> { double b = stack.popD(); double a = stack.popD(); stack.pushI(Double.compare(a, b) < 0 ? -1 : Double.compare(a, b) == 0 ? 0 : 1); }
            case DCMPG -> { double b = stack.popD(); double a = stack.popD(); stack.pushI(Double.compare(a, b) > 0 ? 1 : Double.compare(a, b) == 0 ? 0 : -1); }

            case I2L -> stack.pushL(stack.popI());
            case I2F -> stack.pushF(stack.popI());
            case I2D -> stack.pushD(stack.popI());
            case I2B -> stack.pushI((byte) stack.popI());
            case I2C -> stack.pushI((char) stack.popI());
            case I2S -> stack.pushI((short) stack.popI());
            case L2I -> stack.pushI((int) stack.popL());
            case L2F -> stack.pushF(stack.popL());
            case L2D -> stack.pushD(stack.popL());
            case F2I -> stack.pushI((int) stack.popF());
            case F2L -> stack.pushL((long) stack.popF());
            case F2D -> stack.pushD(stack.popF());
            case D2I -> stack.pushI((int) stack.popD());
            case D2L -> stack.pushL((long) stack.popD());
            case D2F -> stack.pushF((float) stack.popD());

            default -> throw new IllegalStateException("Not an arithmetic/conversion op: " + op);
        }
    }
}
