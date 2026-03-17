package dev.sweety.patch.applier;

import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.hash.Sha256Hash;
import dev.sweety.patch.model.type.PatchTypes;

import java.io.File;
import java.io.IOException;

public class PatchApplierExample {

    public static void main(String[] args) {
        HashFunction hashFunction = new Sha256Hash();

        File oldJar = new File("old-app-1.0.0.jar");
        File outputJar = new File("new-app-patched-1.0.0.jar");

        Applier applier = new Applier(PatchTypes.JSON, hashFunction);

        try {
            applier.patch(oldJar, outputJar, outputJar.getParentFile(), "update1");
        } catch (IOException e) {
            System.err.println("Failed to read patch file: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Failed to apply patch: " + e.getMessage());
        }
    }
}
