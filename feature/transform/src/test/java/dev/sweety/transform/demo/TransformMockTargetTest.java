package dev.sweety.transform.demo;

import dev.sweety.transform.engine.TransformPipeline;
import dev.sweety.transform.transformers.clean.MetadataStripperTransformer;
import dev.sweety.transform.transformers.constant.IntegerEncodingTransformer;
import dev.sweety.transform.transformers.constant.StringEncryptionTransformer;
import dev.sweety.transform.transformers.control.ConditionalMutationTransformer;
import dev.sweety.transform.transformers.control.GotoNormalizationTransformer;
import dev.sweety.transform.transformers.decoy.DecoyClassGenerator;
import dev.sweety.transform.transformers.decoy.DecoyEntanglementTransformer;
import dev.sweety.transform.transformers.remap.ClassRemapAndFlattenTransformer;
import dev.sweety.transform.transformers.remap.ConfusableDictionary;
import dev.sweety.transform.transformers.remap.ConfusableNameGenerator;
import dev.sweety.transform.transformers.remap.ConfusableRemapTransformer;
import dev.sweety.transform.transformers.remap.FieldOverloadCollisionTransformer;
import dev.sweety.transform.transformers.security.AiDecoyInjectionTransformer;
import dev.sweety.transform.transformers.security.AntiTamperTransformer;
import dev.sweety.transform.transformers.security.InvokeDynamicObfuscator;
import dev.sweety.transform.transformers.security.OpaquePredicateTransformer;
import dev.sweety.transform.transformers.virtualize.VirtualizerTransformer;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TransformMockTargetTest {

    @Test
    public void transformMockTargetApp() throws Exception {
        File rootDir = new File(".").getCanonicalFile();
        if (rootDir.getName().equals("transform")) {
            rootDir = rootDir.getParentFile().getParentFile();
        }
        File targetDir = new File(rootDir, "scratch/mock-target");
        File binDir = new File(targetDir, "bin/com/sweety/auth");
        assertTrue(binDir.exists(), "Bin dir must exist");

        byte[] mainBytes;
        byte[] authBytes;
        try (FileInputStream fis = new FileInputStream(new File(binDir, "Main.class"))) { mainBytes = fis.readAllBytes(); }
        try (FileInputStream fis = new FileInputStream(new File(binDir, "LicenseAuthClient.class"))) { authBytes = fis.readAllBytes(); }

        String mainClassName = ConfusableNameGenerator.generate(201, ConfusableDictionary.RN_M, 10);
        String authClassName = ConfusableNameGenerator.generate(202, ConfusableDictionary.OH_ZERO, 10);

        ClassRemapAndFlattenTransformer remapper = new ClassRemapAndFlattenTransformer("a", null, 8);
        remapper.registerMapping("com/sweety/auth/Main", "a/" + mainClassName);
        remapper.registerMapping("com/sweety/auth/LicenseAuthClient", "a/" + authClassName);

        // Generate 8 Stochastic Decoys in package "a/"
        DecoyClassGenerator decoyGen = new DecoyClassGenerator();
        List<DecoyClassGenerator.DecoyClass> rawDecoys = decoyGen.generateBatch(8, "a", null, 8);

        List<String> decoyNames = new ArrayList<>();
        for (DecoyClassGenerator.DecoyClass d : rawDecoys) {
            decoyNames.add(d.getInternalName());
            remapper.registerMapping(d.getInternalName(), d.getInternalName());
        }

        FieldOverloadCollisionTransformer fieldCollisions = new FieldOverloadCollisionTransformer("a");

        TransformPipeline pipeline = TransformPipeline.builder()
                .add(new DecoyEntanglementTransformer(decoyNames))
                .add(new VirtualizerTransformer(false, true))
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
                .add(new AiDecoyInjectionTransformer())
                .build();

        byte[] transformedAuth = pipeline.transform(authBytes, "com/sweety/auth/LicenseAuthClient.class");
        byte[] transformedMain = pipeline.transform(mainBytes, "com/sweety/auth/Main.class");

        File outJar = new File(targetDir, "dist/target-obfuscated.jar");
        outJar.getParentFile().mkdirs();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "a." + mainClassName);

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outJar), manifest)) {
            jos.putNextEntry(new JarEntry("a/" + mainClassName + ".class"));
            jos.write(transformedMain);
            jos.closeEntry();

            jos.putNextEntry(new JarEntry("a/" + authClassName + ".class"));
            jos.write(transformedAuth);
            jos.closeEntry();

            for (DecoyClassGenerator.DecoyClass rawDecoy : rawDecoys) {
                byte[] transformedDecoy = pipeline.transform(rawDecoy.getBytecode(), rawDecoy.getInternalName() + ".class");
                jos.putNextEntry(new JarEntry(rawDecoy.getInternalName() + ".class"));
                jos.write(transformedDecoy);
                jos.closeEntry();
            }

            // Bundle VM runtime classes from feature:transform
            File vmClassesDir = new File(rootDir, "feature/transform/build/classes/java/main");
            if (vmClassesDir.exists()) {
                bundleDir(jos, vmClassesDir, vmClassesDir);
            }
        }

        System.out.println("=================================================================");
        System.out.println("✅ TARGET MOCK HARDENED CON SUCCESSO!");
        System.out.println("📦 JAR Offuscato: " + outJar.getAbsolutePath());
        System.out.println("=================================================================");
    }

    private static void bundleDir(JarOutputStream jos, File baseDir, File currentDir) throws Exception {
        File[] files = currentDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                bundleDir(jos, baseDir, f);
            } else if (f.getName().endsWith(".class")) {
                String relPath = baseDir.toURI().relativize(f.toURI()).getPath();
                if (relPath.startsWith("dev/sweety/transform/vm/")) {
                    try {
                        jos.putNextEntry(new JarEntry(relPath));
                        try (FileInputStream fis = new FileInputStream(f)) {
                            jos.write(fis.readAllBytes());
                        }
                        jos.closeEntry();
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}
