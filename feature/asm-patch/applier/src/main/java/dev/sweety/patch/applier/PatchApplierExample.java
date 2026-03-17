package dev.sweety.patch.applier;

import dev.sweety.patch.format.bin.BinaryPatchReader;
import dev.sweety.patch.format.PatchReader;
import dev.sweety.patch.format.json.JsonPatchReader;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.hash.Sha256Hash;
import dev.sweety.patch.verify.PatchValidator;
import dev.sweety.patch.archive.JarArchive;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class PatchApplierExample {

    public static void main(String[] args) {
        // 1. Setup Dependencies
        PatchReader reader = new JsonPatchReader();
        HashFunction hashFunction = new Sha256Hash();

        // 2. Initialize Applier
        PatchApplier applier = new PatchApplier(reader, hashFunction);

        // 3. Define Files
        File oldJar = new File("old-app-1.0.0.jar");
        File patchFile = new File("update.patch.json");
        File outputJar = new File("new-app-patched-1.0.0.jar");

        try (FileInputStream patchStream = new FileInputStream(patchFile)) {
            System.out.println("Applying patch...");

            // 4. Apply Patch
            applier.apply(oldJar, patchStream, outputJar);

            // 5. Verify (Optional but Recommended)
            System.out.println("Verifying patched jar...");
            
            // Re-read the patch to get expected hashes (stream was consumed)
            try (FileInputStream verifyStream = new FileInputStream(patchFile)) {
                 PatchValidator validator = new PatchValidator(hashFunction);
                 validator.validate(reader.read(verifyStream), new JarArchive(outputJar));
            }

            System.out.println("Patch applied and verified successfully!");
            System.out.println("New JAR created at: " + outputJar.getAbsolutePath());


        } catch (IOException e) {
            System.err.println("Failed to read patch file: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Failed to apply patch: " + e.getMessage());
        }
    }
}
