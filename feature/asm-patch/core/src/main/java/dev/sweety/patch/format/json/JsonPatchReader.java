package dev.sweety.patch.format.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.sweety.patch.format.Header;
import dev.sweety.patch.format.PatchReader;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

public class JsonPatchReader implements PatchReader {

    @Override
    public Patch read(InputStream in) {
        try (DataInputStream dataIn = new DataInputStream(in)) {
            String plain = new String(dataIn.readAllBytes(), StandardCharsets.UTF_8);
            final JsonObject root = Header.GSON.fromJson(plain, JsonObject.class);

            final String header = root.get("header").getAsString();
            if (!Header.V1.header().equals(header)) throw new RuntimeException("Invalid patch file format");


            // Read Versions
            final String fromVersion = root.get("fromVersion").getAsString();
            final String toVersion = root.get("toVersion").getAsString();

            JsonArray operations = root.get("operations").getAsJsonArray();

            if (operations == null) throw new RuntimeException("Invalid patch file format: missing operations");

            // Read Operations
            final int opCount = operations.size();
            final List<PatchOperation> ops = new ArrayList<>((int) (opCount * 1.25)); //allow a minimal treshold for edits

            for (JsonElement operation : operations) {
                ops.add(readOperation(operation.getAsJsonObject()));
            }

            return new Patch(fromVersion, toVersion, ops);

        } catch (IOException e) {
            throw new RuntimeException("Failed to read patch", e);
        }
    }

    private PatchOperation readOperation(JsonObject operation) {
        String typeName = operation.get("type").getAsString().toUpperCase();

        PatchOperation.Type type = PatchOperation.Type.valueOf(typeName);

        String path = getOrElse(operation, "path");
        String hash = getOrElse(operation, "hash");
        byte[] data = getOrElse(operation, "data", this::decode);

        return PatchOperation.builder()
                .type(type)
                .path(path)
                .hash(hash)
                .data(data)
                .build();
    }

    private byte[] decode(String zippedBase64) {
        boolean zip = false;
        if (zippedBase64.startsWith("zip:")) {
            zip = true;
            zippedBase64 = zippedBase64.substring(4);
        }
        byte[] data = Base64.getDecoder().decode(zippedBase64);
        if (zip) {
            try {
                data = Header.unzipFirstFileFromZip(data);
            } catch (IOException e) {
                throw new RuntimeException("Failed to unzip operation data", e);
            }
        }
        return data;
    }

    private static String getOrElse(JsonObject operation, String path) {
        return getOrElse(operation, path, s -> s);
    }

    private static <T> T getOrElse(JsonObject operation, String path, Function<String, T> mapper) {
        JsonElement element = operation.get(path);
        if (element == null || element.isJsonNull()) return null;
        return mapper.apply(element.getAsString());
    }

}

