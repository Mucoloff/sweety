package dev.sweety.patch.exception;

/**
 * Patch payload or post-apply archive does not match expected state (including hash checks).
 */
public class PatchValidationException extends PatchException {

    public PatchValidationException(String message) {
        super(message);
    }

    public PatchValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
