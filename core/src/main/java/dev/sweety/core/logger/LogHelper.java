package dev.sweety.core.logger;

public interface LogHelper {

    void info(Object... input);

    void warn(Object... input);

    void error(Object... input);

    void debug(Object... input);

    void trace(Object... input);

    String getMessage(Object[] input);

}
