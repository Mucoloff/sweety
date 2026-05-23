package dev.sweety.exception;

public class PacketDecodeException extends Except {

    @Deprecated
    public PacketDecodeException(String message, Throwable e) {
        super(message, e);
    }

    @Deprecated
    public PacketDecodeException(String message) {
        super(message);
    }

    @Deprecated
    public PacketDecodeException(Throwable cause) {
        super(cause);
    }

    public static PacketDecodeException of(String message) {
        return new PacketDecodeException(message);
    }

    public static PacketDecodeException of(String message, Throwable cause) {
        return new PacketDecodeException(message, cause);
    }

    public static PacketDecodeException of(Throwable cause) {
        return new PacketDecodeException(cause);
    }
}
