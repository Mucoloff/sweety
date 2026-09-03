package dev.sweety.feature.transform;

import ac.ecstacy.transform.engine.TransformPipeline;
import ac.ecstacy.transform.transformers.clean.MetadataStripperTransformer;
import ac.ecstacy.transform.transformers.constant.IntegerEncodingTransformer;
import ac.ecstacy.transform.transformers.constant.PerMethodSaltedStringTransformer;
import ac.ecstacy.transform.transformers.constant.StringConcatTransformer;
import ac.ecstacy.transform.transformers.constant.StringEncryptionTransformer;
import ac.ecstacy.transform.transformers.control.ControlFlowFlatteningTransformer;
import ac.ecstacy.transform.transformers.control.ExceptionFlowTransformer;
import ac.ecstacy.transform.transformers.control.GotoNormalizationTransformer;
import ac.ecstacy.transform.transformers.decoy.DecoyClassGenerator;
import ac.ecstacy.transform.transformers.decoy.DecoyEntanglementTransformer;
import ac.ecstacy.transform.transformers.inject.BuildInfoInjector;
import ac.ecstacy.transform.transformers.inject.ConstantPoolWatermarkTransformer;
import ac.ecstacy.transform.transformers.manifest.ManifestRemapTransformer;
import ac.ecstacy.transform.transformers.remap.ClassRemapAndFlattenTransformer;
import ac.ecstacy.transform.transformers.remap.ConfusableRegistry;
import ac.ecstacy.transform.transformers.remap.FieldOverloadCollisionTransformer;
import ac.ecstacy.transform.transformers.remap.MethodOverloadCollisionTransformer;
import ac.ecstacy.transform.transformers.security.*;
import ac.ecstacy.transform.transformers.virtualize.VirtualizerTransformer;

import java.util.Random;

/**
 * Sweety Obfuscation Facade & Pipeline Builder.
 * Delegates all bytecode protection, VM zero-boxing, JNI AOT compilation,
 * and Manifest remapping directly to the unified Obfuscator engine.
 */
public final class SweetyObfuscator {

    private SweetyObfuscator() {}

    /**
     * Creates a new Confusable Registry with default look-alike dictionaries and high-entropy depth.
     */
    public static ConfusableRegistry createRegistry(Random random) {
        return new ConfusableRegistry(random, 2, 5, 8);
    }

    /**
     * Creates a standard full-protection pipeline using the unified Obfuscator engine.
     */
    public static TransformPipeline.Builder createPipelineBuilder(ConfusableRegistry registry) {
        return createPipelineBuilder(ObfuscationProfile.FULL, registry);
    }

    /**
     * Creates a customized protection pipeline tailored to the requested ObfuscationProfile.
     */
    public static TransformPipeline.Builder createPipelineBuilder(ObfuscationProfile profile, ConfusableRegistry registry) {
        TransformPipeline.Builder builder = TransformPipeline.builder();
        switch (profile != null ? profile : ObfuscationProfile.FULL) {
            case LIGHTWEIGHT -> builder
                    .add(new MetadataStripperTransformer())
                    .add(new IntegerEncodingTransformer())
                    .add(new StringEncryptionTransformer(true));
            case MINECRAFT_PLUGIN -> builder
                    .add(new FieldOverloadCollisionTransformer(registry))
                    .add(new MethodOverloadCollisionTransformer(registry))
                    .add(new ClassRemapAndFlattenTransformer(registry))
                    .add(new PerMethodSaltedStringTransformer(true, registry))
                    .add(new IntegerEncodingTransformer())
                    .add(new InvokeDynamicObfuscator(registry))
                    .add(new MetadataStripperTransformer());
            case FULL -> builder
                    .add(new FieldOverloadCollisionTransformer(registry))
                    .add(new MethodOverloadCollisionTransformer(registry))
                    .add(new ClassRemapAndFlattenTransformer(registry))
                    .add(new VirtualizerTransformer(false, true, registry))
                    .add(new AntiTamperTransformer())
                    .add(new ControlFlowFlatteningTransformer())
                    .add(new OpaquePredicateTransformer())
                    .add(new PerMethodSaltedStringTransformer(true, registry))
                    .add(new IntegerEncodingTransformer())
                    .add(new DynamicEventExecutorTransformer(registry))
                    .add(new MethodSignatureScramblerTransformer(registry))
                    .add(new InvokeDynamicObfuscator(registry))
                    .add(new MetadataStripperTransformer())
                    .add(new AiDecoyInjectionTransformer(registry));
        }
        return builder;
    }

    /**
     * Creates a manifest remapper for Minecraft plugins and mods (plugin.yml, fabric.mod.json, etc.).
     */
    public static ManifestRemapTransformer createManifestRemapper(ConfusableRegistry registry) {
        return new ManifestRemapTransformer(registry);
    }
}
