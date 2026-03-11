package dev.sweety.versioning.server.injector;

import dev.sweety.util.signature.Signature;
import dev.sweety.util.signature.Watermark;
import dev.sweety.versioning.util.Utils;
import dev.sweety.versioning.version.Version;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class JarPatcher {

    private static final int WATERMARK_SIGNATURE = 0xDEADBEEF;
    private static final String BUILD_INFO_CLASS = "dev.sweety.build.BuildInfo";

    private JarPatcher() {
    }

    public static byte[] patchJar(Path baseJar,
                                  UUID clientId,
                                  Version version,
                                  Map<String, Object> fields,
                                  List<Watermark> watermarks) throws IOException {
        return patchJar(Files.readAllBytes(baseJar), clientId, version, fields, watermarks);
    }

    public static byte[] patchJar(byte[] baseJar,
                                  UUID clientId,
                                  Version version,
                                  Map<String, Object> fields,
                                  List<Watermark> watermarks) throws IOException {
        List<Watermark> merged = new ArrayList<>();
        if (watermarks != null) {
            merged.addAll(watermarks);
        }

        long ts = Instant.now().toEpochMilli();
        merged.add(new Watermark("client", Utils.toBytes(clientId)));
        merged.add(new Watermark("version", Utils.toBytes(version)));
        merged.add(new Watermark("timestamp", Long.toString(ts).getBytes(StandardCharsets.UTF_8)));

        return Signature.applySignatureInMemory(
                baseJar,
                BUILD_INFO_CLASS,
                manifest -> {
                    manifest.getMainAttributes().putValue("Patched", "true");
                    manifest.getMainAttributes().putValue("Patched-For", clientId.toString());
                    manifest.getMainAttributes().putValue("Patched-Version", version.toString());
                    manifest.getMainAttributes().putValue("Patched-At", Long.toString(ts));
                },
                fields,
                merged,
                WATERMARK_SIGNATURE
        );
    }
}