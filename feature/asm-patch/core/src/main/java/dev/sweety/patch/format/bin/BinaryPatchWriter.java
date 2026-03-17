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
            dataOut.write(Header.V1.headerBytes());

            // Write Versions
            writeString(dataOut, patch.getFromVersion());
            writeString(dataOut, patch.getToVersion());

            // Write Operations
            dataOut.writeInt(patch.getOperations().size());
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

        // Path
        writeString(out, op.getPath());

        // Hash
        writeString(out, op.getHash());

        // Data
        byte[] data = op.getData();
        if (data == null) {
            out.writeInt(-1);
        } else {
            out.writeInt(data.length);
            out.write(data);
        }
    }

    private void writeString(DataOutputStream out, String str) throws IOException {
        if (str == null) {
            out.writeInt(-1);
        } else {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            out.writeInt(bytes.length);
            out.write(bytes);
        }
    }
}
