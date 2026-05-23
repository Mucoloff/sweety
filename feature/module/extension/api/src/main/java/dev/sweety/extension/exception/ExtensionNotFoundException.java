package dev.sweety.extension.exception;

import dev.sweety.exception.Except;

public class ExtensionNotFoundException extends Except {

    private final String example;

    public ExtensionNotFoundException(String name, String path, String example) {
        super("No " + name + " info found at " + path + "/" + name + ".json");
        this.example = example;
    }

    public String getExample() {
        return example;
    }
}
