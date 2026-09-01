package dev.sweety.transform.vm.security;

import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Runtime guard against Java Agent dumping, debugging attachment, and unauthorized class modifications.
 */
public final class AntiDumpGuard {

    private static volatile boolean compromised = false;

    private AntiDumpGuard() {}

    public static void verify() {
        if (compromised) {
            throw new SecurityException("Security violation: runtime environment compromised");
        }

        // Check JVM input arguments for common debugger / profiling / agent attachment flags
        try {
            List<String> inputArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : inputArguments) {
                if (arg.startsWith("-agentlib:jdwp") ||
                    arg.startsWith("-Xdebug") ||
                    arg.startsWith("-Xrunjdwp") ||
                    arg.contains("jload") ||
                    arg.contains("dump") ||
                    arg.contains("bytebuddy") ||
                    arg.contains("javaagent:")) {
                    compromised = true;
                    throw new SecurityException("Security violation: debugger or unauthorized agent detected (" + arg + ")");
                }
            }
        } catch (SecurityException se) {
            throw se;
        } catch (Throwable ignored) {
            // If MXBean is restricted or unavailable, proceed
        }
    }

    public static boolean isCompromised() {
        return compromised;
    }

    public static void setCompromised(boolean status) {
        compromised = status;
    }
}
