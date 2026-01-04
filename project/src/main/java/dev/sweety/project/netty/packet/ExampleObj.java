package dev.sweety.project.netty.packet;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ExampleObj implements IExampleObj {

    private int value;
    private String text;

}
