package dev.sweety.extension.exception;

import dev.sweety.exception.Except;

public class ExtensionNotFoundException extends Except {

    public ExtensionNotFoundException(String name, String path, String example) {
        super("No " + name + " info found:");
        setStackTrace(new StackTraceElement[]{
                new StackTraceElement(path, "/"+name + ".json\n", example,-1)
        });
    }
}
