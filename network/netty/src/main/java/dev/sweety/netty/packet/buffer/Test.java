package dev.sweety.netty.packet.buffer;

import dev.sweety.data.buffer.AbstractBuffer;
import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.data.buffer.SegmentBuffer;
import dev.sweety.data.buffer.io.AbstractCodec;

import java.util.Optional;
import java.util.function.Supplier;

public class Test {

    static class Example implements AbstractCodec {

        public Example() {

        }

        public Example(String text, int integer) {
            this.text = text;
            this.integer = integer;
        }

        String text;
        int integer;


        @Override
        public String toString() {
            return "Example{" +
                    "text='" + text + '\'' +
                    ", integer=" + integer +
                    '}';
        }

        @Override
        public void read(BufferReader buffer) {
            text = buffer.readString();
            integer = buffer.readVarInt();
        }

        @Override
        public void write(BufferWriter buffer) {
            buffer.writeString(text);
            buffer.writeVarInt(integer);
        }

    }

    public static void main(String[] args) {
        test(PacketBuffer::new);
        test(SegmentBuffer::new);
        PacketBuffer buff = new PacketBuffer();
        buff.writeString("");
        buff.writeVarInt(0);
        buff.writeOptional(Optional.of(new Example("text", 10)), (a,b) -> b.write(a));
    }

    private static void test(Supplier<AbstractBuffer<?>> supplier) {
        var buff1 = supplier.get();
        var buff2 = supplier.get();
        buff1.writeOptional(Optional.of(new Example("text", 10)));
        read(buff1);
        buff2.writeObject(new Example("text", 10));
        read(buff2);
    }

    private static void read(AbstractBuffer<?> buffer) {
        buffer.markReaderIndex();

        Example example = buffer.readObject(Example::new);
        System.out.println(example);

        buffer.resetReaderIndex();

        Optional<Example> opt = buffer.readOptional(Example::new);

        opt.ifPresentOrElse(value -> System.out.println("Optional is present: " + value), () -> System.out.println("Optional is empty"));
    }

}
