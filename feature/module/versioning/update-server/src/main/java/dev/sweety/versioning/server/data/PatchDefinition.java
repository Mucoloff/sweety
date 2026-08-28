package dev.sweety.versioning.server.data;

import dev.sweety.util.signature.Watermark;
import java.util.List;
import java.util.Map;

public record PatchDefinition(
        Map<String, Object> fields,
        List<Watermark> watermarks,
        Map<String, String> manifestAttributes,
        String targetClass
) {}
