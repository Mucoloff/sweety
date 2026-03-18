package dev.sweety.patch.applier;

import dev.sweety.patch.format.PatchEditor;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.hash.Sha256Hash;
import dev.sweety.patch.model.PatchOperation;
import dev.sweety.patch.model.type.PatchTypes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

public class PatchApplierExample {

    public static void main(String[] args) throws IOException {

        if (true) {

            new PatchEditor(PatchTypes.JSON).edit(new File("patch.patch.json"), patch -> {
                patch.getOperations().forEach(op -> {
                    System.out.println(op.getType());
                    System.out.println(op.getHash());
                    System.out.println(op.getPath());
                    System.out.println();
                });

                patch.getOperations().removeIf(op -> op.getHash().equals("991de88174df362458443f8dfc91607efda951d646f61119854363c2d756f172"));
            });


        }


        HashFunction hashFunction = new Sha256Hash();

        File oldJar = new File("old-app-1.0.0.jar");
        File outputJar = new File("new-app-patched-1.0.0.jar");

        Applier applier = new Applier(PatchTypes.JSON, hashFunction);

        try {
            applier.patch(oldJar, outputJar, outputJar.getParentFile(), "patch");
        } catch (IOException e) {
            System.err.println("Failed to read patch file: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Failed to apply patch: " + e.getMessage());
        }
    }
}
