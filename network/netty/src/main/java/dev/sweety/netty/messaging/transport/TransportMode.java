package dev.sweety.netty.messaging.transport;

public enum TransportMode {
    TCP((byte) 0b01),
    UDP((byte) 0b10),
    DUAL((byte) 0b11);

    public static final byte FLAG_TCP = 0b01;
    public static final byte FLAG_UDP = 0b10;

    private final byte mask;

    TransportMode(byte mask) {
        this.mask = mask;
    }

    public byte mask() {
        return mask;
    }

    public boolean hasTcp() {
        return (mask & FLAG_TCP) != 0;
    }

    public boolean hasUdp() {
        return (mask & FLAG_UDP) != 0;
    }
}
