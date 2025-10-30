package test;

import dev.sweety.network.cloud.packet.buffer.PacketBuffer;

public class TestBuffer {


    public static void main(String[] args) {
        PacketBuffer buffer = new PacketBuffer();
        buffer.writeString("Hello, World!");
        buffer.writeInt(42);



        System.out.println("readable bytes left: " + buffer.readableBytes());



        buffer.insertAtStart(buff -> {
            buff.writeString("stronzo");
        });

        String stronzo = buffer.readString();
        String str = buffer.readString();
        int number = buffer.readInt();

        System.out.println("First string: " + stronzo);
        System.out.println("Second string: " + str);
        System.out.println("Number: " + number);
    }

}
