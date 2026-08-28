package dev.sweety.transform.poc;

import dev.sweety.transform.annotation.Virtualize;

/** Arithmetic/branch/loop/bitwise/object-construction/category-2-stack-op methods for the virtualizer PoC. */
public final class Sample {

	/** Plain POJO used to exercise NEW + INVOKESPECIAL &lt;init&gt; construction inside the VM. */
	public static final class Box {
		private final int value;
		public Box(int value) { this.value = value; }
		public int get() { return value; }
	}

	@Virtualize
	public int add(int a, int b) {
		return a + b;
	}

	@Virtualize
	public int max(int a, int b) {
		return a >= b ? a : b;
	}

	@Virtualize
	public int sumLoop(int n) {
		int s = 0;
		for (int i = 1; i <= n; i++) s += i;
		return s;
	}

	@Virtualize
	public boolean gate(long caps, long bit) {
		return (caps & bit) != 0L;
	}

	/** NEW + DUP + INVOKESPECIAL &lt;init&gt;, then a virtual call on the fresh instance. */
	@Virtualize
	public int makeBox(int v) {
		return new Box(v).get();
	}

	/** Post-increment of a long local: javac emits DUP2 on a single category-2 value. */
	@Virtualize
	public long longPostIncrement(long start) {
		long x = start;
		return x++;
	}

	/** Post-increment of an int array element: javac emits DUP2 on two category-1 values (ref+index). */
	@Virtualize
	public int intArrayPostIncrement(int[] a, int i) {
		return a[i]++;
	}

	/**
	 * Deliberately has a try/catch — MethodSelector.isVirtualizable must reject it (VMInterpreter has
	 * no exception-handler dispatch). Left {@code @Virtualize}d on purpose so the PoC proves the guard
	 * actually fires instead of silently miscompiling.
	 */
	@Virtualize
	public int catchingMethod(int a) {
		try {
			return 10 / a;
		} catch (ArithmeticException e) {
			return -1;
		}
	}
}
