package dev.sweety.patch.model;

public record DeleteOperation(String path, String hash) implements PatchOperation {
    @Override
    public Type type() {
        return Type.DELETE;
    }
}
