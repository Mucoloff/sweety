package dev.sweety.patch.format.bin;

import dev.sweety.patch.format.Header;
import dev.sweety.patch.format.PatchWriter;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class BinaryPatchWriter implements PatchWriter {


    @Override
    public void write(Patch patch, OutputStream out) {
        try (DataOutputStream dataOut = new DataOutputStream(out)) {
            // Write Header
            byte[] header = Header.V1.headerBytes();
            writeVarInt(dataOut, header.length);
            dataOut.write(header);

            // Write Versions
            writeString(dataOut, patch.getFromVersion());
            writeString(dataOut, patch.getToVersion());

            // Write Operations
            writeVarInt(dataOut, patch.getOperations().size());
            for (PatchOperation op : patch.getOperations()) {
                writeOperation(dataOut, op);
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to write patch", e);
        }
    }

    private void writeOperation(DataOutputStream out, PatchOperation op) throws IOException {
        // Type
        out.writeByte(op.getType().ordinal());

        // Method
        out.writeByte(op.getMethod().ordinal());

        // Path
        writeString(out, op.getPath());

        // Hash
        writeString(out, op.getHash());

        // Data
        byte[] data = op.getData();
        if (data == null) {
            writeVarInt(out, -1);
            return;
        }

        boolean zip = false;
        if (data.length >= Header.ZIP_THRESHOLD) {
            try {
                data = Header.zipByteArray(data, "data");
                zip = true;
            } catch (Exception ignored) {
            }
        }

        writeVarInt(out, data.length);
        out.writeByte(zip ? 0x1 : 0);
        out.write(data);
    }

    private void writeString(DataOutputStream out, String string) throws IOException {
        if (string == null) {
            writeVarInt(out, -1);
            return;
        }
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.writeByte((byte) value);
    }
}
