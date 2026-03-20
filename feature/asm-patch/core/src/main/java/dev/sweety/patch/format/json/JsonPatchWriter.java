package dev.sweety.patch.format.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.sweety.patch.format.Header;
import dev.sweety.patch.format.PatchWriter;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;

public class JsonPatchWriter implements PatchWriter {

    @Override
    public void write(Patch patch, OutputStream out) {

        JsonObject root = new JsonObject();

        root.addProperty("header", Header.V1.header());
        root.addProperty("fromVersion", patch.getFromVersion());
        root.addProperty("toVersion", patch.getToVersion());

        JsonArray operations = new JsonArray(patch.getOperations().size());

        for (PatchOperation op : patch.getOperations()) {
            writeOperation(operations, op);
        }

        root.add("operations", operations);

        try {
            out.write(Header.GSON.toJson(root).getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write patch", e);
        }
    }

    private void writeOperation(JsonArray operations, PatchOperation op) {
        JsonObject operation = new JsonObject();

        operation.addProperty("type", op.getType().name().toLowerCase());
        operation.addProperty("path", op.getPath());
        operation.addProperty("hash", op.getHash());

        byte[] data = op.getData();
        if (data != null) {

            boolean zip = false;
            if (data.length >= Header.ZIP_THRESHOLD) {
                try {
                    data = Header.zipByteArray(data, "data");
                    zip = true;
                } catch (Exception ignored) {
                }
            }

            String encoded = Base64.getEncoder().encodeToString(data);

            if (zip) encoded = "zip:" + encoded;

            operation.addProperty("data", encoded);

        } else {
            operation.add("data", null);
        }

        operations.add(operation);
    }

}
