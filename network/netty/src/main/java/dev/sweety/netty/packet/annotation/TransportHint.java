package dev.sweety.netty.packet.annotation;

import dev.sweety.netty.messaging.transport.TransportMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TransportHint {
    TransportMode value() default TransportMode.TCP;
}
