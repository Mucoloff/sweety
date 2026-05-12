package dev.sweety.netty.packet.buffer;

import dev.sweety.netty.packet.buffer.io.Codec;

import java.util.Optional;

public class Test {

    static class Example implements Codec {

        public Example() {

        }

        public Example(String text, int integer) {
            this.text = text;
            this.integer = integer;
        }

        String text;
        int integer;

        @Override
        public void read(PacketBuffer buffer) {
            text = buffer.readString();
            integer = buffer.readVarInt();
        }

        @Override
        public void write(PacketBuffer buffer) {
            buffer.writeString(text);
            buffer.writeVarInt(integer);
        }

        @Override
        public String toString() {
            return "Example{" +
                    "text='" + text + '\'' +
                    ", integer=" + integer +
                    '}';
        }
    }

    public static void main(String[] args) {
        PacketBuffer buffer = new PacketBuffer();

        // writeOptional / writeObject(null|value) and readObject / readOptional share one packed-boolean marker + payload.
        //buffer.writeObject(new Example("text", 10));
        buffer.writeOptional(Optional.of(new Example("text", 10)));

        buffer.markReaderIndex();

        Example example = buffer.readObject(Example::new);
        System.out.println(example);

        buffer.resetReaderIndex();

        Optional<Example> opt = buffer.readOptional(Example::new);

        opt.ifPresentOrElse(value -> System.out.println("Optional is present: " + value), () -> System.out.println("Optional is empty"));


    }

}
