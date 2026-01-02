package dev.sweety.cloud.messaging.exception;

import dev.sweety.core.exception.Except;

public class PacketEncodeException extends Except {

    public PacketEncodeException(String message, Throwable e) {
        super(message, e);
    }

    public PacketEncodeException(String message) {
        super(message);
    }

    public PacketEncodeException(Throwable cause) {
        super(cause);
    }
}
