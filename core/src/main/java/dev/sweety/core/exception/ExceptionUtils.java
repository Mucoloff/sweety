package dev.sweety.core.exception;

import lombok.experimental.UtilityClass;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Consumer;

@UtilityClass
public class ExceptionUtils {

    @SafeVarargs
    public <T> void throwIfAnyEquals(String message, T ifEquals, T... toCheck) {
        for (T o : toCheck) {
            if (o == ifEquals) throw new IllegalArgumentException(message);
        }
    }

    public static <T> T throwSilently(ThrowingSupplier<T> func, Consumer<Throwable> errorHandler) {
        try {
            return func.get();
        } catch (Throwable t) {
            errorHandler.accept(t);
            return null;
        }
    }

    public static <T> T throwSilently(ThrowingSupplier<T> func) {
        return throwSilently(func, k -> {});
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Throwable;
    }

    public static String getStackTrace(final Throwable throwable) {
        if (throwable == null) return "";
        final StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw, true));
        return sw.toString();
    }
}
