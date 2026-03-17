package dev.sweety.patch.model;

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