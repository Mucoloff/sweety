package dev.sweety.patch.model;

import lombok.Getter;

public sealed interface PatchOperation permits AddOperation, ModifyOperation, DeleteOperation {

    enum Type {
        ADD,
        MODIFY,
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