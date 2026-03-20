package dev.sweety.util.logger;

public interface LogHelper {

    LogHelper info(Object... input);

    LogHelper warn(Object... input);

    LogHelper error(Object... input);

    LogHelper debug(Object... input);

    LogHelper trace(Object... input);

}
