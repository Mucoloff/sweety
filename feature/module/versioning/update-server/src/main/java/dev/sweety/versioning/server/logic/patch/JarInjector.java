package dev.sweety.versioning.server.logic.patch;

import dev.sweety.util.signature.Signature;
import dev.sweety.versioning.server.logic.cache.CacheKey;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;

public final class JarInjector {

    private JarInjector() {
    }

    public static byte[] inject(Path baseJar, PatchDefinition patch) throws IOException {
        return inject(Files.readAllBytes(baseJar), patch);
    }

    public static byte[] inject(byte[] baseJar, PatchDefinition patch) throws IOException {
        if (patch == null) return baseJar;

        return Signature.applySignatureInMemory(
                baseJar,
                patch.targetClass(),
                manifest -> {
                    Attributes mainAttributes = manifest.getMainAttributes();
                    patch.manifestAttributes().forEach(mainAttributes::putValue);
                },
                patch.fields(),
                patch.watermarks(),
                Signature.WATERMARK_SIGNATURE
        );
    }
}