package dev.sweety.project.netty.packet;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ExampleObjImpl implements ExampleObj {

    private int value;
    private String text;

}
