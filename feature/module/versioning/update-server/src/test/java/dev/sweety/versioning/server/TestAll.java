package dev.sweety.versioning.server;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        CacheManagerTest.class,
        ReleaseManagerParserTest.class,
        UpdateServerIntegrationTest.class,
        WebhookE2EIntegrationTest.class,
        WebhookGuardsTest.class,
        WebhookHandlerTest.class
})
public class TestAll {

}
