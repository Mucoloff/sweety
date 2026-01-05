package dev.sweety.packet.processor;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface BuildPacket {

    //changes the name of the element
    String name() default "";

    //adds annotations to the Element
    Class<? extends Annotation>[] annotations() default {};

    //if applied on field and name is not empty, adds a getter method with the new name
    boolean addMethod() default false;
}
