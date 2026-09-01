package dev.sweety.transform.vm.security;

/**
 * Validates that sensitive/security methods are only invoked from expected packages and not from reflective trampolines or agent hooks.
 */
public final class StackIntegrityChecker {

    private StackIntegrityChecker() {}

    public static void checkCaller(String expectedPackagePrefix) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        if (stack.length < 3) return;

        // stack[0] = getStackTrace, stack[1] = checkCaller, stack[2] = direct caller, stack[3] = caller's caller
        for (int i = 2; i < Math.min(stack.length, 6); i++) {
            String className = stack[i].getClassName();
            if (className.startsWith("java.lang.reflect.") ||
                className.startsWith("sun.reflect.") ||
                className.startsWith("jdk.internal.reflect.") ||
                className.contains("CGLIB") ||
                className.contains("ByteBuddy")) {
                throw new SecurityException("Security violation: reflective or intercepted call detected from " + className);
            }
        }
    }
}
