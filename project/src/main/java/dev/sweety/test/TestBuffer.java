package dev.sweety.test;

import dev.sweety.netty.packet.buffer.PacketBuffer;

public class TestBuffer {


    public static void main(String[] args) throws Throwable {
        PacketBuffer buffer = new PacketBuffer();
        buffer.writeString("Hello, World!");
        buffer.writeVarInt(42);



        System.out.println("readable bytes left: " + buffer.readableBytes());

        buffer.wrapData(buff -> buff.writeString("stronzo"));

        String stronzo = buffer.readString();
        String str = buffer.readString();
        int number = buffer.readVarInt();

        System.out.println("First string: " + stronzo);
        System.out.println("Second string: " + str);
        System.out.println("Number: " + number);
    }

}
