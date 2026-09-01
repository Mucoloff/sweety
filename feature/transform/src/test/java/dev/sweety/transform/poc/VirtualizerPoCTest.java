package dev.sweety.transform.poc;

import dev.sweety.util.signature.SessionCrypto;
import dev.sweety.transform.engine.TransformPipeline;
import dev.sweety.transform.transformers.virtualize.VirtualBytecodeService;
import dev.sweety.transform.transformers.virtualize.VirtualizerTransformer;
import dev.sweety.transform.vm.SessionFoldSource;
import dev.sweety.transform.vm.VMInterpreter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PoC: virtualize {@link Sample}'s methods (arithmetic/branch/loop/bitwise, object construction, and
 * category-1/category-2 stack-op-inducing patterns), reload, and assert behaviour is preserved.
 * Validates VirtualizerTransformer + VMCompiler (frame-analysis-driven DUP2/DUP_X2/POP2 variant
 * selection) + VMInterpreter (including real NEW/&lt;init&gt; construction via {@code PendingNew}).
 */
class VirtualizerPoCTest {

	@AfterEach
	void unbindSessionFold() {
		VMInterpreter.bindSessionFold(null); // never leak a binding into other tests
	}

	private static final class ByteLoader extends ClassLoader {
		ByteLoader() { super(VirtualizerPoCTest.class.getClassLoader()); }
		Class<?> define(String name, byte[] b) { return defineClass(name, b, 0, b.length); }
	}

	private static byte[] sampleBytes() throws Exception {
		try (InputStream in = Sample.class.getResourceAsStream("Sample.class")) {
			if (in == null) throw new IllegalStateException("Sample.class not found");
			return in.readAllBytes();
		}
	}

	private static Class<?> virtualize() throws Exception {
		return virtualize(false);
	}

	private static Class<?> virtualize(boolean gated) throws Exception {
		byte[] original = sampleBytes();
		TransformPipeline pipeline = TransformPipeline.builder().add(new VirtualizerTransformer(gated)).build();
		byte[] transformed = pipeline.transform(original, "dev/sweety/transform/poc/Sample.class");
		assertFalse(Arrays.equals(original, transformed), "virtualizer left bytes unchanged");
		return new ByteLoader().define("dev.sweety.transform.poc.Sample", transformed);
	}

	private static Object call(Object inst, String name, Class<?>[] sig, Object... args) throws Exception {
		Method m = inst.getClass().getDeclaredMethod(name, sig);
		m.setAccessible(true);
		return m.invoke(inst, args);
	}

	@Test
	void virtualizedPureMethodsMatchPlain() throws Exception {
		Object vi = virtualize().getDeclaredConstructor().newInstance();
		Sample plain = new Sample();

		assertEquals(plain.add(7, 5), call(vi, "add", new Class[]{int.class, int.class}, 7, 5));
		assertEquals(plain.max(7, 5), call(vi, "max", new Class[]{int.class, int.class}, 7, 5));
		assertEquals(plain.max(2, 9), call(vi, "max", new Class[]{int.class, int.class}, 2, 9));
		assertEquals(plain.sumLoop(10), call(vi, "sumLoop", new Class[]{int.class}, 10));
		assertEquals(plain.gate(0b1010L, 0b0010L), call(vi, "gate", new Class[]{long.class, long.class}, 0b1010L, 0b0010L));
		assertEquals(plain.gate(0b1010L, 0b0100L), call(vi, "gate", new Class[]{long.class, long.class}, 0b1010L, 0b0100L));
	}

	@Test
	void virtualizedObjectConstructionMatchesPlain() throws Exception {
		Object vi = virtualize().getDeclaredConstructor().newInstance();
		Sample plain = new Sample();

		assertEquals(plain.makeBox(42), call(vi, "makeBox", new Class[]{int.class}, 42));
		assertEquals(plain.makeBox(-7), call(vi, "makeBox", new Class[]{int.class}, -7));
	}

	@Test
	void virtualizedCategory2StackOpsMatchPlain() throws Exception {
		Object vi = virtualize().getDeclaredConstructor().newInstance();
		Sample plain = new Sample();

		// DUP2_CAT2: post-increment of a single long value.
		assertEquals(plain.longPostIncrement(10L), call(vi, "longPostIncrement", new Class[]{long.class}, 10L));

		// DUP2_CAT1: post-increment of an int array element (ref + index, two category-1 values).
		int[] plainArr = {5, 6, 7};
		int[] viArr = {5, 6, 7};
		assertEquals(plain.intArrayPostIncrement(plainArr, 1), call(vi, "intArrayPostIncrement",
				new Class[]{int[].class, int.class}, viArr, 1));
		assertEquals(plainArr[1], viArr[1], "array side-effect must match (element actually incremented)");
	}

	@Test
	void virtualizedMethodRunsWithFreshBoundSession() throws Exception {
		VMInterpreter.bindSessionFold(new SessionFoldSource() {
			@Override public boolean fresh() { return true; }
			@Override public long fold(long salt) { return salt; }
		});

		Object vi = virtualize().getDeclaredConstructor().newInstance();
		assertEquals(new Sample().add(7, 5), call(vi, "add", new Class[]{int.class, int.class}, 7, 5));
	}

	@Test
	void virtualizedMethodAbortsWithStaleBoundSession() throws Exception {
		VMInterpreter.bindSessionFold(new SessionFoldSource() {
			@Override public boolean fresh() { return false; }
			@Override public long fold(long salt) { return salt; }
		});

		Object vi = virtualize().getDeclaredConstructor().newInstance();
		Method m = vi.getClass().getDeclaredMethod("add", int.class, int.class);
		m.setAccessible(true);
		InvocationTargetException ex = assertThrows(InvocationTargetException.class,
				() -> m.invoke(vi, 7, 5));
		assertEquals(IllegalStateException.class, ex.getCause().getClass());
	}

	@Test
	void tryCatchMethodIsSkippedNotMiscompiled() throws Exception {
		Object vi = virtualize().getDeclaredConstructor().newInstance();
		Sample plain = new Sample();

		// catchingMethod carries @Virtualize but has a try/catch — MethodSelector.isVirtualizable must
		// reject it, leaving the ORIGINAL bytecode in place (no VM stub), so behaviour still matches
		// plain exactly, including the caught-exception path.
		assertEquals(plain.catchingMethod(2), call(vi, "catchingMethod", new Class[]{int.class}, 2));
		assertEquals(plain.catchingMethod(0), call(vi, "catchingMethod", new Class[]{int.class}, 0));
	}

	@Test
	void gatedVirtualizerLeavesFieldUnsetUntilUnlocked() throws Exception {
		Class<?> cls = virtualize(true);
		Object vi = cls.getDeclaredConstructor().newInstance();

		// Locked: the storage field is null, executing throws before running any attacker-visible logic.
		Method m = cls.getDeclaredMethod("add", int.class, int.class);
		m.setAccessible(true);
		InvocationTargetException locked = assertThrows(InvocationTargetException.class,
				() -> m.invoke(vi, 7, 5));
		assertEquals(IllegalStateException.class, locked.getCause().getClass());

		// Unlock: server-side recompile+encrypt (VirtualBytecodeService) against the ORIGINAL class
		// bytes, then the client-side decrypt+reflective-field-set that would follow the real
		// RequestVirtualBytecodeTransaction round trip.
		byte[] epochKey = new byte[32];
		Arrays.fill(epochKey, (byte) 0x42);
		Map<String, byte[]> ciphertexts = VirtualBytecodeService.compileAndEncrypt(sampleBytes(), epochKey);
		assertTrue(ciphertexts.size() >= 7, "one entry per @Virtualize method in Sample");

		for (Map.Entry<String, byte[]> e : ciphertexts.entrySet()) {
			byte[] plaintext = SessionCrypto.decrypt(e.getValue(), epochKey);
			Field field = cls.getDeclaredField(e.getKey());
			field.setAccessible(true);
			field.set(null, plaintext);
		}

		assertEquals(new Sample().add(7, 5), call(vi, "add", new Class[]{int.class, int.class}, 7, 5));
	}
}
