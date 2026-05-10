package dev.sweety.patch.model;

public record ModifyOperation(String path, String hash, byte[] data, Method method) implements PatchOperation {
    @Override
    public Type type() {
        return Type.MODIFY;
    }
}
