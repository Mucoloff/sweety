package dev.sweety.patch;

import java.util.ArrayList;
import java.util.List;

public final class ClassPatch {

    private final String targetInternalName;
    private final List<MethodPatch> methodPatches = new ArrayList<>();

    public ClassPatch(String targetInternalName) {
        this.targetInternalName = targetInternalName.replace('.', '/');
    }

    public static ClassPatch of(String targetClass) {
        return new ClassPatch(targetClass);
    }

    public ClassPatch patchMethod(MethodPatch patch) {
        methodPatches.add(patch);
        return this;
    }

    public String targetInternalName() { return targetInternalName; }
    public List<MethodPatch> methodPatches() { return methodPatches; }
}
