package dev.sweety.extension.manager;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        ExtensionTest.class,
        ExtensionInfoTest.class,
        DownloadFileTest.class,
        ExtensionManagerTest.class,
        ExtensionClassLoaderTest.class,
        IntegrationTest.class
})
public class ModuleTests {
    // Test suite runner
}
