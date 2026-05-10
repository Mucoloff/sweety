package dev.sweety.versioning.client;

import dev.sweety.versioning.client.artifact.Artifact;
import dev.sweety.versioning.client.artifact.ArtifactRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArtifactRegistryTest {

    @Test
    void testTenantIsolation() {
        ArtifactRegistry registry = new ArtifactRegistry();
        
        Artifact artifact = new Artifact("test-app", "1.0.0");
        
        registry.registerSecret("tenant-1", artifact, "secret-1");
        registry.registerSecret("tenant-2", artifact, "secret-2");
        
        assertEquals("secret-1", registry.getSecret("tenant-1", artifact));
        assertEquals("secret-2", registry.getSecret("tenant-2", artifact));
        assertNull(registry.getSecret("unknown", artifact));
    }
}
