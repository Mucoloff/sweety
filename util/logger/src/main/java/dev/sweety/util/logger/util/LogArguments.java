package dev.sweety.util.logger.util;

import dev.sweety.color.AnsiColor;
import dev.sweety.exception.ExceptionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

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

    /**
     * Returns the trailing Throwable if it has no corresponding {} placeholder,
     * null otherwise. Mirrors SLF4J behaviour.
     */
    public static Throwable trailingThrowable(Object[] args) {
        if (!isPattern(args) || args.length < 2) return null;
        Object last = args[args.length - 1];
        if (!(last instanceof Throwable)) return null;
        int placeholders = countPlaceholders(pattern(args));
        int paramCount = args.length - 1;
        return placeholders < paramCount ? (Throwable) last : null;
    }

    /**
     * Substitutes {} placeholders in the pattern. Every substituted value and
     * every extra arg beyond the available placeholders is rendered through
     * {@link #stringify}. Trailing Throwable without a placeholder is excluded
     * from substitution (caller appends it separately via
     * {@link #trailingThrowable}).
     */
    public static String formatMessage(Object[] args) {
        if (!isPattern(args)) {
            if (args == null || args.length == 0) return "";
            StringJoiner sj = new StringJoiner(" ");
            for (Object arg : args) sj.add(stringify(arg));
            return sj.toString();
        }

        String pattern = pattern(args);
        Object[] params = params(args);

        // Determine how many params are available for substitution
        int activeParams = params.length;
        if (activeParams > 0 && params[activeParams - 1] instanceof Throwable) {
            int placeholders = countPlaceholders(pattern);
            if (placeholders < activeParams) activeParams--;
        }

        StringBuilder sb = new StringBuilder();
        int pi = 0;
        for (int i = 0; i < pattern.length(); ) {
            if (i + 1 < pattern.length() && pattern.charAt(i) == '{' && pattern.charAt(i + 1) == '}') {
                sb.append(pi < activeParams ? stringify(params[pi++]) : "{}");
                i += 2;
            } else {
                sb.append(pattern.charAt(i++));
            }
        }
        // Append any extra params not consumed by {} — render via stringify
        while (pi < activeParams) {
            sb.append(' ').append(stringify(params[pi++]));
        }
        return sb.toString();
    }

    public static int countPlaceholders(String pattern) {
        int count = 0, i = 0;
        while ((i = pattern.indexOf("{}", i)) >= 0) { count++; i += 2; }
        return count;
    }

    /** Converts any log argument to a human-readable string. */
    public static String stringify(Object part) {
        return switch (part) {
            case null -> "<null>";
            case String s -> s;
            case AnsiColor color -> color.color();
            case Class<?> clazz -> clazz.getSimpleName();
            case Throwable e -> ExceptionUtils.getStackTrace(e);
            case Object[] arr -> Arrays.deepToString(arr);
            case List<?> list -> list.toString();
            case Set<?> set -> set.toString();
            case Map<?, ?> m -> m.toString();
            case Iterable<?> it -> {
                StringJoiner sj = new StringJoiner(", ", "[", "]");
                for (Object o : it) sj.add(String.valueOf(o));
                yield sj.toString();
            }
            default -> part.toString();
        };
    }
}
