package dev.sweety.patch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClassPatch {

    private final String targetInternalName;
    private final List<MethodPatch> methodPatches = new ArrayList<>();
    private final Map<String, List<MethodPatch>> patchesBySignature = new HashMap<>();
    private final Map<String, List<MethodPatch>> patchesByNameOnly = new HashMap<>();

    public ClassPatch(String targetInternalName) {
        this.targetInternalName = targetInternalName.replace('.', '/');
    }

    public static ClassPatch of(String targetClass) {
        return new ClassPatch(targetClass);
    }

    public ClassPatch patchMethod(MethodPatch patch) {
        methodPatches.add(patch);
        if (patch.methodDesc() != null) {
            patchesBySignature.computeIfAbsent(patch.methodName() + "#" + patch.methodDesc(), k -> new ArrayList<>()).add(patch);
        } else {
            patchesByNameOnly.computeIfAbsent(patch.methodName(), k -> new ArrayList<>()).add(patch);
        }
        return this;
    }

    public List<MethodPatch> findPatches(String name, String desc) {
        List<MethodPatch> exact = patchesBySignature.get(name + "#" + desc);
        List<MethodPatch> wildcard = patchesByNameOnly.get(name);

        if (exact == null && wildcard == null) return Collections.emptyList();
        if (exact != null && wildcard == null) return exact;
        if (exact == null) return wildcard;

        List<MethodPatch> combined = new ArrayList<>(exact.size() + wildcard.size());
        combined.addAll(exact);
        combined.addAll(wildcard);
        return combined;
    }

    public String targetInternalName() { return targetInternalName; }
    public List<MethodPatch> methodPatches() { return methodPatches; }
}
