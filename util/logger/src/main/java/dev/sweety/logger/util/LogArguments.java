package dev.sweety.logger.util;

public final class LogArguments {

    private LogArguments() {}

    public static boolean isPattern(Object[] args) {
        return args != null && args.length > 0 && args[0] instanceof String;
    }

    public static String pattern(Object[] args) {
        return (String) args[0];
    }

    public static Object[] params(Object[] args) {
        if (args.length <= 1) return new Object[0];
        Object[] out = new Object[args.length - 1];
        System.arraycopy(args, 1, out, 0, args.length - 1);
        return out;
    }
}

