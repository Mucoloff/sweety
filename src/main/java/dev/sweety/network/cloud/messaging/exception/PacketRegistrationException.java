package dev.sweety.network.cloud.messaging.exception;

import dev.sweety.core.exception.Except;

public class PacketRegistrationException extends Except {

    public PacketRegistrationException(String message) {
        super(message);
    }

    public PacketRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }

    public PacketRegistrationException(Throwable cause) {
        super(cause);
    }
}