package dev.sweety.patch.model;

import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
public class PatchOperation {

    public enum Type {
        ADD,
        MODIFY,
        DELETE
    }

    public enum Method {
        REPLACEMENT,
        TEXT_DIFF
    }

    private Type type;
    @lombok.Builder.Default
    private Method method = Method.REPLACEMENT;
    private String path;
    private byte[] data;   // null per DELETE
    private String hash;
}