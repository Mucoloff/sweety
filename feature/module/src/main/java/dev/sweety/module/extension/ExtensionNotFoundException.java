package dev.sweety.module.extension;

import dev.sweety.core.exception.Except;

public class ExtensionNotFoundException extends Except {

    public ExtensionNotFoundException(String name, String path, String example) {
        super("No " + Extension.NAME + " info found:");
        setStackTrace(new StackTraceElement[]{
                new StackTraceElement(path, "/"+name + ".json\n", example,-1)
        });
    }
}
