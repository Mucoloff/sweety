package dev.sweety.transform.demo;

import dev.sweety.transform.annotation.SecurityCritical;
import dev.sweety.transform.annotation.Transform;
import dev.sweety.transform.engine.TransformPipeline;
import dev.sweety.transform.transformers.constant.IntegerEncodingTransformer;
import dev.sweety.transform.transformers.constant.StringEncryptionTransformer;
import dev.sweety.transform.transformers.control.ConditionalMutationTransformer;
import dev.sweety.transform.transformers.control.GotoNormalizationTransformer;
import dev.sweety.transform.transformers.remap.ClassRemapAndFlattenTransformer;
import dev.sweety.transform.transformers.remap.ConfusableDictionary;
import dev.sweety.transform.transformers.remap.ConfusableRemapTransformer;
import dev.sweety.transform.transformers.remap.FieldOverloadCollisionTransformer;
import dev.sweety.transform.transformers.security.AntiTamperTransformer;
import dev.sweety.transform.transformers.security.InvokeDynamicObfuscator;
import dev.sweety.transform.transformers.security.OpaquePredicateTransformer;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ExportDecompilationDemoTest {

    @Transform
    @SecurityCritical
    public static class ClientSecurityPayload {
        public int health = 100;
        public String sessionToken = "SWEETY-SECRET-TOKEN-998877";
        public byte[] payloadKey = new byte[] { 0x13, 0x37, 0x42 };
        public long expirationTimestamp = 1750000000000L;
        public boolean isVip = true;

        public String decryptLicense(String inputLicense, int salt) {
            if (inputLicense == null || inputLicense.isEmpty()) {
                return "ERR_EMPTY_LICENSE";
            }
            int checksum = calculateInternalChecksum(inputLicense, salt);
            if (checksum == 42) {
                return "LICENSE_VALID:" + sessionToken + ":" + (health * 2);
            }
            return "LICENSE_INVALID";
        }

        private int calculateInternalChecksum(String license, int salt) {
            int hash = salt;
            for (char c : license.toCharArray()) {
                hash = (hash * 31) ^ c;
            }
            return (hash & 0x7F) == 42 ? 42 : 0;
        }

        private void internalSecurityRoutine() {
            System.out.println("Executing internal security routine for: " + sessionToken);
        }
    }

    @Test
    public void exportObfuscatedClassFile() throws Exception {
        String internalName = ClientSecurityPayload.class.getName().replace('.', '/');
        byte[] originalBytes;
        try (InputStream is = ClientSecurityPayload.class.getResourceAsStream("ExportDecompilationDemoTest$ClientSecurityPayload.class")) {
            assertNotNull(is, "Target class bytecode must be found");
            originalBytes = is.readAllBytes();
        }

        // Full Pipeline with Field Overload Collision, Homoglyph Remap (Ill / O0 / rnm), String Encryption, Opaque Predicates, Anti-Tamper, Indy & Package Flattening
        ClassRemapAndFlattenTransformer remapper = new ClassRemapAndFlattenTransformer("a", ConfusableDictionary.ILL, 8);

        TransformPipeline pipeline = TransformPipeline.builder()
                .add(new AntiTamperTransformer())
                .add(new OpaquePredicateTransformer())
                .add(new GotoNormalizationTransformer())
                .add(new ConditionalMutationTransformer())
                .add(new StringEncryptionTransformer())
                .add(new IntegerEncodingTransformer())
                .add(new FieldOverloadCollisionTransformer("a"))
                .add(new ConfusableRemapTransformer(ConfusableDictionary.ILL, 12))
                .add(new InvokeDynamicObfuscator())
                .add(remapper)
                .build();

        byte[] obfuscatedBytes = pipeline.transform(originalBytes, internalName + ".class");

        // Generate 6 Decoy / Honey-pot classes in the same flattened package "a/"
        dev.sweety.transform.transformers.decoy.DecoyClassGenerator decoyGen = new dev.sweety.transform.transformers.decoy.DecoyClassGenerator();
        java.util.List<dev.sweety.transform.transformers.decoy.DecoyClassGenerator.DecoyClass> decoys =
                decoyGen.generateBatch(6, "a", ConfusableDictionary.ILL, 8);

        // Build a complete JAR with the real obfuscated class + all decoy classes
        File desktopJar = new File("/Users/francesco/Desktop/ClientSecurityPayload_HARDENED_DECOYS.jar");
        try (java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(new FileOutputStream(desktopJar))) {
            // Write main remapped class (e.g. a/IllIIlIl.class)
            String mainEntryName = remapper.getMappings().getOrDefault(internalName, "a/IllIIlIl") + ".class";
            jos.putNextEntry(new java.util.jar.JarEntry(mainEntryName));
            jos.write(obfuscatedBytes);
            jos.closeEntry();

            // Write all decoy classes
            for (dev.sweety.transform.transformers.decoy.DecoyClassGenerator.DecoyClass decoy : decoys) {
                jos.putNextEntry(new java.util.jar.JarEntry(decoy.getInternalName() + ".class"));
                jos.write(decoy.getBytecode());
                jos.closeEntry();
            }
        }

        System.out.println("=================================================================");
        System.out.println("✅ JAR CON CLASSI CONFUSABILI, FLATTEN PACKAGE & DECOYS GENERATO:");
        System.out.println("📦 JAR Desktop:  " + desktopJar.getAbsolutePath());
        System.out.println("🔑 Entry Reale:  " + remapper.getMappings().get(internalName) + ".class");
        System.out.println("🛡 Decoys Count: " + decoys.size() + " classi fake nel package a/");
        System.out.println("=================================================================");
    }
}
