package dev.sweety.packet.processor;

import dev.sweety.netty.packet.buffer.io.CallableDecoder;
import dev.sweety.netty.packet.buffer.io.CallableEncoder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldBuffer {

    Class<? extends CallableEncoder<?>> encoder();

    Class<? extends CallableDecoder<?>> decoder();

}