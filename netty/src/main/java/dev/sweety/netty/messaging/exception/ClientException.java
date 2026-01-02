package dev.sweety.netty.messaging.exception;

import dev.sweety.core.exception.Except;

public class ClientException extends Except {
    public ClientException(String message, Throwable e) {
        super(message, e);
    }

    public ClientException(String message) {
        super(message);
    }

    public ClientException(Throwable cause) {
        super(cause);
    }
}
