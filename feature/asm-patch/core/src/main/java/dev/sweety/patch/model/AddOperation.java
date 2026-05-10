package dev.sweety.patch.model;

public record AddOperation(String path, String hash, byte[] data) implements PatchOperation {
    @Override
    public Type type() {
        return Type.ADD;
    }
}
