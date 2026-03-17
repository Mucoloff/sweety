package dev.sweety.patch.format.bin;

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
import java.util.List;

public class BinaryPatchReader implements PatchReader {

    @Override
    public Patch read(InputStream in) {
        try (DataInputStream dataIn = new DataInputStream(in)) {
            // Read Header
            byte[] header = new byte[4];
            dataIn.readFully(header);
            if (!Arrays.equals(Header.V1.headerBytes(), header)) {
                throw new RuntimeException("Invalid patch file format");
            }

            // Read Versions
            String fromVersion = readString(dataIn);
            String toVersion = readString(dataIn);

            // Read Operations
            int opCount = dataIn.readInt();
            List<PatchOperation> ops = new ArrayList<>(opCount);
            
            for (int i = 0; i < opCount; i++) {
                ops.add(readOperation(dataIn));
            }

            return new Patch(fromVersion, toVersion, ops);

        } catch (IOException e) {
            throw new RuntimeException("Failed to read patch", e);
        }
    }

    private PatchOperation readOperation(DataInputStream in) throws IOException {
        // Type
        int typeOrdinal = in.readByte();
        PatchOperation.Type type = PatchOperation.Type.values()[typeOrdinal];

        // Path
        String path = readString(in);

        // Hash
        String hash = readString(in);

        // Data
        byte[] data = null;
        int dataLen = in.readInt();
        if (dataLen != -1) {
            data = new byte[dataLen];
            in.readFully(data);
        }

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

