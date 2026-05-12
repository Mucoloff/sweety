package dev.sweety.patch.exception;

/**
 * Invalid patch file format or corrupt patch stream.
 */
public class PatchFormatException extends PatchException {

    public PatchFormatException(String message) {
        super(message);
    }

    public PatchFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
