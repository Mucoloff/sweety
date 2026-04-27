package dev.sweety.sql4j.api.util;

import java.util.function.Consumer;

public interface SqlLogger {

    void log(String message);

    default void log(String message, Object... args) {
        log(String.format(message, args));
    }

    static SqlLogger stdout(String prefix) {
        return message -> System.out.println("[" + prefix + "] " + message);
    }

    static SqlLogger stdout() {
        return stdout("SQL4J");
    }

    static SqlLogger nop() {
        return _ -> {
        };
    }

    static SqlLogger custom(Consumer<String> consumer) {
        return consumer::accept;
    }
}
