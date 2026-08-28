package dev.sweety.patch.model;

public record MoveOperation(String oldPath, String newPath, String hash) implements PatchOperation {

    @Override
    public Type type() {
        return Type.MOVE;
    }

    @Override
    public String path() {
        return newPath;
    }

    public Method method() {
        throw new UnsupportedOperationException("Operation has no method");
    }

    public byte[] data() {
        throw new UnsupportedOperationException("Operation has no data");
    }
}