package dev.sweety.patch.model;

import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class Patch {
    private String fromVersion;
    private String toVersion;
    private List<PatchOperation> operations;
}
