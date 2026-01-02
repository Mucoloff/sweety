package dev.sweety.netty.messaging.exception;

import dev.sweety.core.exception.Except;

public class ClientInvalid extends Except {
    public ClientInvalid(String message, Throwable e) {
        super(message, e);
    }

    public ClientInvalid(String message) {
        super(message);
    }

    public ClientInvalid(Throwable cause) {
        super(cause);
    }
}
