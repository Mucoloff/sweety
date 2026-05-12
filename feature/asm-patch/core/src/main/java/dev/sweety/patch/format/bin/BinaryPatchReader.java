package dev.sweety.patch.format.bin;

import dev.sweety.patch.exception.PatchFormatException;
import dev.sweety.patch.format.Header;
import dev.sweety.patch.format.PatchReader;
import dev.sweety.patch.model.Patch;
import dev.sweety.patch.model.PatchOperation;
import dev.sweety.patch.model.AddOperation;
import dev.sweety.patch.model.ModifyOperation;
import dev.sweety.patch.model.DeleteOperation;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BinaryPatchReader implements PatchReader {

    @Override
    public Patch read(InputStream in) {
        try (DataInputStream dataIn = new DataInputStream(in)) {
            // Read Header
            byte[] header = new byte[readVarInt(dataIn)];
            dataIn.readFully(header);
            if (!Arrays.equals(Header.V1.headerBytes(), header)) {
                throw new PatchFormatException("Invalid patch file format");
            }

            // Read Versions
            String fromVersion = readString(dataIn);
            String toVersion = readString(dataIn);

            // Read Operations
            int opCount = readVarInt(dataIn);
            final List<PatchOperation> ops = new ArrayList<>((int) (opCount * 1.25)); //allow a minimal treshold for edits

            for (int i = 0; i < opCount; i++) {
                ops.add(readOperation(dataIn));
            }

            return new Patch(fromVersion, toVersion, ops);

        } catch (IOException e) {
            throw new PatchFormatException("Failed to read patch", e);
        }
    }

    private PatchOperation readOperation(DataInputStream in) throws IOException {
        // Type
        int typeOrdinal = in.readByte();
        PatchOperation.Type type = PatchOperation.Type.values()[typeOrdinal];

        // Method
        int methodOrdinal = in.readByte();
        PatchOperation.Method method = PatchOperation.Method.values()[methodOrdinal];

        // Path
        String path = readString(in);

        // Hash
        String hash = readString(in);

        // Data
        byte[] data = null;
        int dataLen = readVarInt(in);
        if (dataLen != -1) {
            boolean zip = (in.readByte() & 0x1) == 0x1;

            data = new byte[dataLen];
            in.readFully(data);
            if (zip) data = Header.unzipFirstFileFromZip(data);
        }

        return switch (type) {
            case ADD -> new AddOperation(path, hash, data);
            case MODIFY -> new ModifyOperation(path, hash, data, method);
            case DELETE -> new DeleteOperation(path, hash);
        };
    }

    private String readString(DataInputStream in) throws IOException {
        int len = readVarInt(in);
        if (len == -1) return null;
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private int readVarInt(DataInputStream in) throws IOException {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = in.readByte();
            int value = (read & 0x7F);
            result |= (value << (7 * numRead));

            numRead++;
            if (numRead > 5) {
                throw new PatchFormatException("VarInt is too big");
            }
        } while ((read & 0x80) != 0);

        return result;
    }
}
