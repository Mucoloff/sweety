package dev.sweety.transform.demo;

import dev.sweety.transform.engine.TransformPipeline;
import dev.sweety.transform.transformers.clean.MetadataStripperTransformer;
import dev.sweety.transform.transformers.constant.IntegerEncodingTransformer;
import dev.sweety.transform.transformers.constant.StringEncryptionTransformer;
import dev.sweety.transform.transformers.control.ConditionalMutationTransformer;
import dev.sweety.transform.transformers.control.GotoNormalizationTransformer;
import dev.sweety.transform.transformers.decoy.DecoyClassGenerator;
import dev.sweety.transform.transformers.remap.ClassRemapAndFlattenTransformer;
import dev.sweety.transform.transformers.remap.ConfusableDictionary;
import dev.sweety.transform.transformers.remap.ConfusableNameGenerator;
import dev.sweety.transform.transformers.remap.ConfusableRemapTransformer;
import dev.sweety.transform.transformers.remap.FieldOverloadCollisionTransformer;
import dev.sweety.transform.transformers.security.AntiTamperTransformer;
import dev.sweety.transform.transformers.security.InvokeDynamicObfuscator;
import dev.sweety.transform.transformers.security.OpaquePredicateTransformer;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TransformSampleAppTest {

    @Test
    public void transformSampleApp() throws Exception {
        File rootDir = new File(".").getCanonicalFile();
        if (rootDir.getName().equals("transform")) {
            rootDir = rootDir.getParentFile().getParentFile(); // back to root
        }
        File scratchDir = new File(rootDir, "scratch/test-app");
        File binDir = new File(scratchDir, "bin/com/example/demo");
        assertTrue(binDir.exists(), "Sample app bin directory must exist at: " + binDir.getAbsolutePath());

        File appClassFile = new File(binDir, "App.class");
        File secClassFile = new File(binDir, "SecurityManager.class");

        byte[] appBytes;
        byte[] secBytes;
        try (FileInputStream fis = new FileInputStream(appClassFile)) { appBytes = fis.readAllBytes(); }
        try (FileInputStream fis = new FileInputStream(secClassFile)) { secBytes = fis.readAllBytes(); }

        // Generate diverse confusable class names for real classes
        String mainClassName = ConfusableNameGenerator.generate(101, ConfusableDictionary.RN_M, 10);
        String secClassName = ConfusableNameGenerator.generate(102, ConfusableDictionary.OH_ZERO, 10);

        // Setup global package flattening mappings
        ClassRemapAndFlattenTransformer remapper = new ClassRemapAndFlattenTransformer("a", null, 8);
        remapper.registerMapping("com/example/demo/App", "a/" + mainClassName);
        remapper.registerMapping("com/example/demo/SecurityManager", "a/" + secClassName);

        FieldOverloadCollisionTransformer fieldCollisions = new FieldOverloadCollisionTransformer("a");

        // Unified full protection pipeline with mixed confusable dictionaries (ILL, OH_ZERO, RN_M, PQ_DB, NH)
        TransformPipeline pipeline = TransformPipeline.builder()
                .add(new AntiTamperTransformer())
                .add(new OpaquePredicateTransformer())
                .add(new GotoNormalizationTransformer())
                .add(new ConditionalMutationTransformer())
                .add(new StringEncryptionTransformer(true))
                .add(new IntegerEncodingTransformer())
                .add(fieldCollisions)
                .add(new ConfusableRemapTransformer(null, 12))
                .add(remapper)
                .add(new InvokeDynamicObfuscator())
                .add(new MetadataStripperTransformer())
                .add(new dev.sweety.transform.transformers.security.AiDecoyInjectionTransformer())
                .build();

        byte[] transformedSec = pipeline.transform(secBytes, "com/example/demo/SecurityManager.class");
        byte[] transformedApp = pipeline.transform(appBytes, "com/example/demo/App.class");

        // Generate 6 Stochastic Decoys in package "a/" using mixed dictionaries
        DecoyClassGenerator decoyGen = new DecoyClassGenerator();
        List<DecoyClassGenerator.DecoyClass> rawDecoys = decoyGen.generateBatch(6, "a", null, 8);

        File outJar = new File(scratchDir, "dist/app-transformed.jar");
        outJar.getParentFile().mkdirs();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "a." + mainClassName);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outJar), manifest)) {
            // Write main app
            jos.putNextEntry(new JarEntry("a/" + mainClassName + ".class"));
            jos.write(transformedApp);
            jos.closeEntry();

            // Write security manager
            jos.putNextEntry(new JarEntry("a/" + secClassName + ".class"));
            jos.write(transformedSec);
            jos.closeEntry();

            // Write transformed decoys
            for (DecoyClassGenerator.DecoyClass rawDecoy : rawDecoys) {
                byte[] transformedDecoy = pipeline.transform(rawDecoy.getBytecode(), rawDecoy.getInternalName() + ".class");
                jos.putNextEntry(new JarEntry(rawDecoy.getInternalName() + ".class"));
                jos.write(transformedDecoy);
                jos.closeEntry();
            }
        }

        System.out.println("=================================================================");
        System.out.println("✅ MINI-PROGETTO TRASFORMATO CON SUCCESSO!");
        System.out.println("📦 JAR Offuscato:   " + outJar.getAbsolutePath());
        System.out.println("🚀 Main Class Remap: a." + mainClassName);
        System.out.println("🔑 Sec Class Remap:  a." + secClassName);
        System.out.println("=================================================================");
    }
}
