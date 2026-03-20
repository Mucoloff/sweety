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

    private Type type;
    private String path;
    private byte[] data;   // null per DELETE
    private String hash;
}