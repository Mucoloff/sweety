package dev.sweety.sql4j.api.util;

import java.util.function.Consumer;

/**
 * Simple logging callback invoked by {@link dev.sweety.sql4j.api.connection.SqlRunner}
 * before and after every SQL execution.
 *
 * <p>Register a logger globally:
 * <pre>{@code
 * SqlRunner.setLogger(SqlLogger.stdout());          // plain stdout
 * SqlRunner.setLogger(msg -> log.debug(msg));       // SLF4J / Log4j bridge
 * SqlRunner.setLogger(SqlLogger.nop());             // silence (default)
 * }</pre>
 *
 * <p>The {@code volatile} field in {@code SqlRunner} ensures the logger is immediately
 * visible across all worker threads without further synchronisation.
 */
public interface SqlLogger {

    /**
     * Called with a fully-formatted log message.
     *
     * @param message the pre-formatted message (never {@code null})
     */
    void log(String message);

    /**
     * Formats {@code message} using {@link String#format(String, Object...)} and
     * delegates to {@link #log(String)}.
     *
     * @param message a {@link java.util.Formatter} format string
     * @param args    format arguments
     */
    default void log(String message, Object... args) {
        log(String.format(message, args));
    }

    /**
     * Returns a logger that prints to {@link System#out} with a custom prefix.
     *
     * @param prefix the bracketed label prepended to every line
     * @return a new {@code SqlLogger} writing to stdout
     */
    static SqlLogger stdout(String prefix) {
        return message -> System.out.println("[%s] %s".formatted(prefix, message));
    }

    /**
     * Returns a logger that prints to {@link System#out} with the default {@code [SQL4J]} prefix.
     *
     * @return a new {@code SqlLogger} writing to stdout
     */
    static SqlLogger stdout() {
        return stdout("SQL4J");
    }

    /**
     * Returns a no-op logger that discards all messages.
     * This is the global default until a logger is installed via
     * {@link dev.sweety.sql4j.api.connection.SqlRunner#setLogger}.
     *
     * @return a shared no-op {@code SqlLogger}
     */
    static SqlLogger nop() {
        return _ -> {};
    }

    /**
     * Wraps an arbitrary {@link Consumer Consumer&lt;String&gt;} as a {@code SqlLogger}.
     * Use this to bridge to any logging framework:
     * <pre>{@code SqlLogger.custom(logger::info)}</pre>
     *
     * @param consumer the message consumer
     * @return a {@code SqlLogger} backed by the given consumer
     */
    static SqlLogger custom(Consumer<String> consumer) {
        return consumer::accept;
    }
}
