package dev.sweety.network.cloud.impl;

public enum PacketRegistry {
    TEXT(0x1),
    FILE(0x2),

    ;
    public static final PacketRegistry[] VALUES = values();

    private final byte index;

    PacketRegistry(int index) {
        this.index = ((byte) index);
    }

    public byte id() {
        return index;
    }
}
