package dev.sweety.versioning.client;

import dev.sweety.versioning.version.VersionParseTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        VersionParseTest.class
})
public class VersioningTestSuite {
}
