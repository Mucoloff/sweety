package dev.sweety.patch.generator;

import dev.sweety.patch.bytecode.AsmClassNormalizer;
import dev.sweety.patch.bytecode.ClassNormalizer;
import dev.sweety.patch.diff.PatchFilter;
import dev.sweety.patch.filter.DefaultPatchFilter;
import dev.sweety.patch.hash.HashFunction;
import dev.sweety.patch.hash.Sha256Hash;
import dev.sweety.patch.model.type.PatchTypes;

import java.io.File;
import java.io.IOException;

public class PatchGeneratorExample {

    public static void main(String[] args) {
        HashFunction hashFunction = new Sha256Hash();
        ClassNormalizer normalizer = new AsmClassNormalizer();
        DefaultPatchFilter f = new DefaultPatchFilter();
        PatchFilter filter = path -> {
            //if (f.exclude(path)) return true;
            System.out.println("visited " + path);
            return false;
        };

        Generator generator = new Generator(hashFunction, normalizer, PatchTypes.JSON);

        File oldJar = new File("storage/cache/app/stable/0/0/1/ad921d60-4863-3625-8809-553a3db49a4a.jar");
        File newJar = new File("storage/cache/app/stable/0/0/2/ad921d60-4863-3625-8809-553a3db49a4a.jar");

        try {
            generator.generate(oldJar, newJar, oldJar.getParentFile(), "test", "1.0", "2.0", filter);
        } catch (IOException e) {
            System.err.println("Error generating patch: " + e.getMessage());
            // In production use a logger
        }
    }
}
