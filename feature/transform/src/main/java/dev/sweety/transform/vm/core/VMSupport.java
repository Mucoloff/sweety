package dev.sweety.transform.vm.core;

import dev.sweety.transform.vm.state.PendingNew;
import dev.sweety.transform.vm.state.VMStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Low-level helpers shared by the VM's op-family executors.
 */
public final class VMSupport {

    private VMSupport() {}

    public static int unboxI(Object o) {
        if (o instanceof Integer i) return i;
        if (o instanceof Boolean b) return b ? 1 : 0;   // boolean/byte/char/short are all int-width on the VM stack
        if (o instanceof Character c) return c;
        if (o instanceof Number n) return n.intValue();
        return 0;
    }
    public static long   unboxL(Object o) { return o instanceof Long l ? l : o instanceof Number n ? n.longValue() : 0L; }
    public static float  unboxF(Object o) { return o instanceof Float f ? f : o instanceof Number n ? n.floatValue() : 0f; }
    public static double unboxD(Object o) { return o instanceof Double d ? d : o instanceof Number n ? n.doubleValue() : 0.0; }

    public static String readString(ByteBuffer buf) {
        int len = buf.getShort() & 0xFFFF;
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static Class<?> classFor(Map<String, Object> cache, String internal) throws ClassNotFoundException {
        String key = "C:" + internal;
        Class<?> c = (Class<?>) cache.get(key);
        if (c == null) {
            c = Class.forName(internal.replace('/', '.'));
            cache.put(key, c);
        }
        return c;
    }

    public static Method findMethod(Class<?> cls, String name, String desc) throws NoSuchMethodException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && buildMethodDescriptor(m).equals(desc)) return m;
            }
        }
        for (Class<?> iface : cls.getInterfaces()) {
            for (Method m : iface.getDeclaredMethods()) {
                if (m.getName().equals(name) && buildMethodDescriptor(m).equals(desc)) return m;
            }
        }
        throw new NoSuchMethodException(cls.getName() + "." + name + desc);
    }

    public static Constructor<?> findConstructor(Class<?> cls, String desc) throws NoSuchMethodException {
        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            if (buildCtorDescriptor(c).equals(desc)) return c;
        }
        throw new NoSuchMethodException(cls.getName() + ".<init>" + desc);
    }

    public static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {} // walking hierarchy, absence expected
        }
        throw new NoSuchFieldException(cls.getName() + "." + name);
    }

    public static String buildMethodDescriptor(Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : m.getParameterTypes()) appendTypeDesc(sb, p);
        sb.append(')');
        appendTypeDesc(sb, m.getReturnType());
        return sb.toString();
    }

    public static String buildCtorDescriptor(Constructor<?> c) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : c.getParameterTypes()) appendTypeDesc(sb, p);
        sb.append(")V");
        return sb.toString();
    }

    /**
     * Appends the JVM type descriptor for {@code c} to {@code sb}.
     * Handles primitives, void, arrays (any depth), and reference types.
     * No external dependencies — pure Java reflection.
     */
    public static void appendTypeDesc(StringBuilder sb, Class<?> c) {
        if (c == void.class)    { sb.append('V'); return; }
        if (c == boolean.class) { sb.append('Z'); return; }
        if (c == byte.class)    { sb.append('B'); return; }
        if (c == char.class)    { sb.append('C'); return; }
        if (c == short.class)   { sb.append('S'); return; }
        if (c == int.class)     { sb.append('I'); return; }
        if (c == long.class)    { sb.append('J'); return; }
        if (c == float.class)   { sb.append('F'); return; }
        if (c == double.class)  { sb.append('D'); return; }
        if (c.isArray()) {
            // Class.getName() for arrays is already JVM format ([I, [[I, [Ljava.lang.String; ...)
            // Just replace '.' with '/' for reference component types.
            sb.append(c.getName().replace('.', '/'));
            return;
        }
        // Reference type: Ljava/lang/String;
        sb.append('L').append(c.getName().replace('.', '/')).append(';');
    }

    public static Object coerce(Object val, Class<?> target) {
        if (val == null || !target.isPrimitive()) return val;
        if (target == int.class)    return unboxI(val);
        if (target == long.class)   return unboxL(val);
        if (target == float.class)  return unboxF(val);
        if (target == double.class) return unboxD(val);
        if (target == boolean.class) return unboxI(val) != 0;
        if (target == byte.class)   return (byte) unboxI(val);
        if (target == char.class)   return (char) unboxI(val);
        if (target == short.class)  return (short) unboxI(val);
        return val;
    }

    /**
     * One type tag per parameter, in declared order: {@code 'J'/'F'/'D'} for long/float/double,
     * {@code 'L'} for any reference (object or array), {@code 'I'} for everything else
     * (int/boolean/byte/char/short — all int-width on the VM stack, matching the real JVM's own
     * local/stack conventions, e.g. {@code ILOAD}/{@code ICONST} for booleans).
     */
    public static char[] paramTypeTags(String desc) {
        int open = desc.indexOf('(');
        int close = desc.indexOf(')');
        if (open < 0 || close < 0) return new char[0];
        it.unimi.dsi.fastutil.chars.CharArrayList tags = new it.unimi.dsi.fastutil.chars.CharArrayList();
        int i = open + 1;
        while (i < close) {
            char c = desc.charAt(i);
            if (c == 'J' || c == 'F' || c == 'D') { tags.add(c); i++; }
            else if (c == 'L') { tags.add('L'); i = desc.indexOf(';', i) + 1; }
            else if (c == '[') { int j = i; while (desc.charAt(j) == '[') j++; tags.add('L'); i = (desc.charAt(j) == 'L') ? desc.indexOf(';', j) + 1 : j + 1; }
            else { tags.add('I'); i++; } // Z/B/C/S/I
        }
        char[] out = new char[tags.size()];
        for (int k = 0; k < out.length; k++) out[k] = tags.getChar(k);
        return out;
    }

    /** Type tag (see {@link #paramTypeTags}) for a single resolved {@link Class}. */
    public static char tagOf(Class<?> c) {
        if (c == long.class) return 'J';
        if (c == float.class) return 'F';
        if (c == double.class) return 'D';
        if (!c.isPrimitive()) return 'L';
        return 'I';
    }

    /** Pop a value of the given tag's shape off {@code stack}, unwrapping a pending-construction ref. */
    public static Object popByTag(VMStack stack, char tag) {
        return switch (tag) {
            case 'J' -> stack.popL();
            case 'F' -> stack.popF();
            case 'D' -> stack.popD();
            case 'L' -> PendingNew.unwrap(stack.popRef());
            default  -> stack.popI(); // 'I' — Z/B/C/S/I all int-width
        };
    }

    /** Push a reflection result of public static type {@code type} onto {@code stack}, boxed only at this call boundary. */
    public static void pushTyped(VMStack stack, Class<?> type, Object result) {
        if (type == void.class) return;
        if (type == long.class)   { stack.pushL((Long) result); return; }
        if (type == float.class)  { stack.pushF((Float) result); return; }
        if (type == double.class) { stack.pushD((Double) result); return; }
        if (!type.isPrimitive())  { stack.pushRef(result); return; }
        if (type == boolean.class) stack.pushI(((Boolean) result) ? 1 : 0);
        else if (type == char.class) stack.pushI((Character) result);
        else stack.pushI(((Number) result).intValue()); // int/byte/short
    }

    /** Local-slot width per parameter from a method descriptor: 2 for long/double, 1 otherwise. */
    public static int[] paramSlotWidths(String desc) {
        int open = desc.indexOf('(');
        int close = desc.indexOf(')');
        if (open < 0 || close < 0) return new int[0];
        it.unimi.dsi.fastutil.ints.IntArrayList widths = new it.unimi.dsi.fastutil.ints.IntArrayList();
        int i = open + 1;
        while (i < close) {
            char c = desc.charAt(i);
            if (c == 'J' || c == 'D') { widths.add(2); i++; }
            else if (c == 'L') { widths.add(1); i = desc.indexOf(';', i) + 1; }
            else if (c == '[') { int j = i; while (desc.charAt(j) == '[') j++; widths.add(1); i = (desc.charAt(j) == 'L') ? desc.indexOf(';', j) + 1 : j + 1; }
            else { widths.add(1); i++; }
        }
        int[] out = new int[widths.size()];
        for (int k = 0; k < out.length; k++) out[k] = widths.getInt(k);
        return out;
    }
}
