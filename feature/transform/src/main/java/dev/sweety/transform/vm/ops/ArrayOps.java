package dev.sweety.transform.vm.ops;
import dev.sweety.transform.vm.core.VmOp;
import dev.sweety.transform.vm.core.VMSupport;
import dev.sweety.transform.vm.state.PendingNew;
import dev.sweety.transform.vm.state.VMLocals;
import dev.sweety.transform.vm.state.VMStack;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.Map;

import static dev.sweety.transform.vm.core.VMSupport.classFor;
import static dev.sweety.transform.vm.core.VMSupport.readString;

/** Array element load/store and array allocation (NEWARRAY/ANEWARRAY). Array references are always
 * unwrapped via {@link PendingNew#unwrap} for consistency, though in practice only NEW-constructed
 * objects (never arrays) can be pending. */
public final class ArrayOps {

    private ArrayOps() {}

    public static void execute(VmOp op, VMStack stack, Map<String, Object> cache, ByteBuffer buf) throws ClassNotFoundException {
        switch (op) {
            case AALOAD  -> { int i = stack.popI(); Object arr = PendingNew.unwrap(stack.popRef()); stack.pushRef(Array.get(arr, i)); }
            case AASTORE -> { Object val = PendingNew.unwrap(stack.popRef()); int i = stack.popI(); Object arr = PendingNew.unwrap(stack.popRef()); Array.set(arr, i, val); }
            case IALOAD  -> { int i = stack.popI(); Object arr = PendingNew.unwrap(stack.popRef()); stack.pushI(Array.getInt(arr, i)); }
            case IASTORE -> { int val = stack.popI(); int i = stack.popI(); Object arr = PendingNew.unwrap(stack.popRef()); Array.setInt(arr, i, val); }
            case LALOAD  -> { int i = stack.popI(); Object arr = PendingNew.unwrap(stack.popRef()); stack.pushL(Array.getLong(arr, i)); }
            case LASTORE -> { long val = stack.popL(); int i = stack.popI(); Object arr = PendingNew.unwrap(stack.popRef()); Array.setLong(arr, i, val); }
            case FALOAD  -> { int i = stack.popI(); Object arr = PendingNew.unwrap(stack.popRef()); stack.pushF(Array.getFloat(arr, i)); }
            case FASTORE -> { float val = stack.popF(); int i = stack.popI(); Object arr = PendingNew.unwrap(stack.popRef()); Array.setFloat(arr, i, val); }
            case DALOAD  -> { int i = stack.popI(); Object arr = PendingNew.unwrap(stack.popRef()); stack.pushD(Array.getDouble(arr, i)); }
            case DASTORE -> { double val = stack.popD(); int i = stack.popI(); Object arr = PendingNew.unwrap(stack.popRef()); Array.setDouble(arr, i, val); }
            case BALOAD  -> { int i = stack.popI(); Object arr = PendingNew.unwrap(stack.popRef()); stack.pushI((int) (byte) Array.getByte(arr, i)); }
            case BASTORE -> { byte val = (byte) stack.popI(); int i = stack.popI(); Object arr = PendingNew.unwrap(stack.popRef()); Array.setByte(arr, i, val); }
            case CALOAD  -> { int i = stack.popI(); Object arr = PendingNew.unwrap(stack.popRef()); stack.pushI((int) (char) Array.getChar(arr, i)); }
            case CASTORE -> { char val = (char) stack.popI(); int i = stack.popI(); Object arr = PendingNew.unwrap(stack.popRef()); Array.setChar(arr, i, val); }
            case SALOAD  -> { int i = stack.popI(); Object arr = PendingNew.unwrap(stack.popRef()); stack.pushI((int) (short) Array.getShort(arr, i)); }
            case SASTORE -> { short val = (short) stack.popI(); int i = stack.popI(); Object arr = PendingNew.unwrap(stack.popRef()); Array.setShort(arr, i, val); }
            case NEWARRAY -> {
                int type = buf.get() & 0xFF;
                int len = stack.popI();
                stack.pushRef(newPrimitiveArray(type, len));
            }
            case ANEWARRAY -> {
                String cls = readString(buf);
                int len = stack.popI();
                stack.pushRef(Array.newInstance(classFor(cache, cls), len));
            }
            default -> throw new IllegalStateException("Not an array op: " + op);
        }
    }

    private static Object newPrimitiveArray(int type, int len) {
        return switch (type) {
            case 4  -> new boolean[len];
            case 5  -> new char[len];
            case 6  -> new float[len];
            case 7  -> new double[len];
            case 8  -> new byte[len];
            case 9  -> new short[len];
            case 10 -> new int[len];
            case 11 -> new long[len];
            default -> throw new IllegalArgumentException("Unknown array type: " + type);
        };
    }
}
