package dev.sweety.module.extension;

public class ExtensionNotFoundException extends RuntimeException {

    public ExtensionNotFoundException(String name, String path, String example) {
        super("No " + Extension.NAME + " info found:");
        setStackTrace(new StackTraceElement[]{
                new StackTraceElement(path, "/"+name + ".json\n", example,-1)
        });
    }
}
