package dev.sweety.feature.transform;

import ac.ecstacy.transform.engine.TransformPipeline;
import ac.ecstacy.transform.transformers.remap.ConfusableRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Random;

public class SweetyObfuscatorProfileTest {

    @Test
    public void testProfilePipelineConstruction() {
        ConfusableRegistry registry = SweetyObfuscator.createRegistry(new Random(42));

        TransformPipeline full = SweetyObfuscator.createPipelineBuilder(ObfuscationProfile.FULL, registry).build();
        Assertions.assertNotNull(full);
        Assertions.assertTrue(full.transformers().size() > 5, "Full pipeline must contain multiple layers");

        TransformPipeline lightweight = SweetyObfuscator.createPipelineBuilder(ObfuscationProfile.LIGHTWEIGHT, registry).build();
        Assertions.assertNotNull(lightweight);
        Assertions.assertTrue(lightweight.transformers().size() < full.transformers().size(),
                "Lightweight pipeline must have fewer transformers than full");

        TransformPipeline mcPlugin = SweetyObfuscator.createPipelineBuilder(ObfuscationProfile.MINECRAFT_PLUGIN, registry).build();
        Assertions.assertNotNull(mcPlugin);
        Assertions.assertNotNull(SweetyObfuscator.createManifestRemapper(registry));
    }
}
