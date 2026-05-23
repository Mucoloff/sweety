package dev.sweety.project.netty.packet;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class DefaultExampleObj implements ExampleObj {

    private int value;
    private String text;

}
