package dev.sweety.patch.model;

public sealed interface PatchOperation permits AddOperation, ModifyOperation, MoveOperation, DeleteOperation {

    enum Type {
        ADD,
        MODIFY,
        MOVE,
        DELETE
    }

    enum Method {
        REPLACEMENT,
        TEXT_DIFF
    }

    Type type();
    String path();
    String hash();
    
    default Method method() {
        return Method.REPLACEMENT;
    }

    default byte[] data() {
        return null;
    }
}