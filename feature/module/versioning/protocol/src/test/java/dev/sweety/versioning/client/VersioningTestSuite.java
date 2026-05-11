package dev.sweety.versioning.client;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
    ArtifactRegistryTest.class
})
public class VersioningTestSuite {
}
