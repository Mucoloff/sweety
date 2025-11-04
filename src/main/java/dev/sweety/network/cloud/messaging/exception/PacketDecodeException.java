package dev.sweety.network.cloud.messaging.exception;

public class PacketDecodeException extends RuntimeException {
    public PacketDecodeException(String message, Throwable e) {
        super(message, e);
    }

    public PacketDecodeException(String message) {
        super(message);
    }
}
