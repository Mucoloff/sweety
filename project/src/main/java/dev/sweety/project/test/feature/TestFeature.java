package dev.sweety.project.test.feature;

import dev.sweety.module.feature.OptionalFeature;

public class TestFeature implements OptionalFeature {
    @Override
    public void execute() {
        System.out.println("TestFeature eseguita con successo!");
    }

    @Override
    public String getFeatureName() {
        return "TestFeature";
    }
}
