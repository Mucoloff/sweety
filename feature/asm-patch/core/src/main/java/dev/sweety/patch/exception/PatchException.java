package dev.sweety.patch.exception;

/**
 * Base failure for patch apply, read, write, or archive access.
 */
public class PatchException extends RuntimeException {

    public PatchException(String message) {
        super(message);
    }

    public PatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
