package dev.sweety.netty.messaging.exception;

import dev.sweety.core.exception.Except;

public class PacketDecodeException extends Except {

    public PacketDecodeException(String message, Throwable e) {
        super(message, e);
    }

    public PacketDecodeException(String message) {
        super(message);
    }

    public PacketDecodeException(Throwable cause) {
        super(cause);
    }
}
