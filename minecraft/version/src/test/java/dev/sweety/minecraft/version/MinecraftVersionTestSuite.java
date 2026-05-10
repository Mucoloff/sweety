package dev.sweety.minecraft.version;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
    MinecraftVersionTest.class,
    MajorVersionTest.class,
    VersionComparisonTest.class
})
public class MinecraftVersionTestSuite {
}
