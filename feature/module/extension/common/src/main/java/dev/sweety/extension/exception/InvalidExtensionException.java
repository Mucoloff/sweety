package dev.sweety.extension.exception;

import dev.sweety.core.exception.Except;
import dev.sweety.extension.Extension;

public class InvalidExtensionException extends Except {

    public <T extends Extension> InvalidExtensionException(Class<?> main, String message, Class<T> parent) {
        super(main.getSimpleName() + " " + message + " " + parent.getSimpleName());
    }

    public InvalidExtensionException(String message, Exception parent) {
        super(message, parent);
    }
}
