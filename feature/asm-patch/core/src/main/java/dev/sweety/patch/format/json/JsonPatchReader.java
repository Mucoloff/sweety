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

public class JsonPatchReader implements PatchReader {

    @Override
    public Patch read(InputStream in) {
        try (DataInputStream dataIn = new DataInputStream(in)) {
            final JsonObject root = Header.GSON.fromJson(new String(dataIn.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);

            final String header = root.get("header").getAsString();
            if (!Header.V1.header().equals(header)) throw new RuntimeException("Invalid patch file format");


            // Read Versions
            final String fromVersion = root.get("fromVersion").getAsString();
            final String toVersion = root.get("toVersion").getAsString();

            JsonArray operations = root.get("operations").getAsJsonArray();

            if (operations == null) throw new RuntimeException("Invalid patch file format: missing operations");

            // Read Operations
            int opCount = operations.size();
            List<PatchOperation> ops = new ArrayList<>(opCount);

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
        String path = operation.get("path").getAsString();
        String hash = operation.get("hash").getAsString();

        JsonElement dataElement = operation.get("data");

        byte[] data = dataElement == null || dataElement.isJsonNull() ? null : Base64.getDecoder().decode(dataElement.getAsString());

        return PatchOperation.builder()
                .type(type)
                .path(path)
                .hash(hash)
                .data(data)
                .build();
    }

    private String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len == -1) {
            return null;
        }
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

