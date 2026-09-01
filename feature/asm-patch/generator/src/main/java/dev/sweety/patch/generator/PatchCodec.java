package dev.sweety.patch.generator;

import dev.sweety.patch.ClassPatch;
import dev.sweety.patch.InjectionPoint;
import dev.sweety.patch.MethodPatch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Compact binary serialization for ClassPatch metadata.
 */
public final class PatchCodec {

    public static final int MAGIC = 0x50544348; // "PTCH"

    private PatchCodec() {}

    public static byte[] encode(ClassPatch patch) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.writeInt(MAGIC);
            out.writeUTF(patch.targetInternalName());
            out.writeInt(patch.methodPatches().size());

            for (MethodPatch mp : patch.methodPatches()) {
                out.writeUTF(mp.methodName());
                out.writeUTF(mp.methodDesc() != null ? mp.methodDesc() : "");
                out.writeUTF(mp.injectionPoint().name());
                out.writeUTF(mp.targetOwner() != null ? mp.targetOwner() : "");
                out.writeUTF(mp.targetName() != null ? mp.targetName() : "");
                out.writeUTF(mp.targetDesc() != null ? mp.targetDesc() : "");
            }
        }
        return baos.toByteArray();
    }

    public static ClassPatch decode(byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IOException("Invalid patch magic header: 0x" + Integer.toHexString(magic));
            }
            String target = in.readUTF();
            ClassPatch patch = ClassPatch.of(target);

            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                String mName = in.readUTF();
                String mDesc = in.readUTF();
                if (mDesc.isEmpty()) mDesc = null;
                InjectionPoint ip = InjectionPoint.valueOf(in.readUTF());
                String tOwner = in.readUTF();
                if (tOwner.isEmpty()) tOwner = null;
                String tName = in.readUTF();
                if (tName.isEmpty()) tName = null;
                String tDesc = in.readUTF();
                if (tDesc.isEmpty()) tDesc = null;

                patch.patchMethod(new MethodPatch(mName, mDesc, ip, tOwner, tName, tDesc, (mv, op) -> {}));
            }
            return patch;
        }
    }
}
