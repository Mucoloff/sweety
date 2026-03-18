package dev.sweety.versioning.server.logic.patch;

import dev.sweety.util.signature.Watermark;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;

public record PatchDefinition(
        Map<String, Object> fields,
        List<Watermark> watermarks,
        Map<String, String> manifestAttributes,
        String targetClass
) {}

