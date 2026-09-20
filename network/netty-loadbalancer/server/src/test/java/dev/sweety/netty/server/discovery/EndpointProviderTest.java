package dev.sweety.netty.server.discovery;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EndpointProviderTest {

    @Test
    void testStaticEndpointProvider() {
        InetSocketAddress addr1 = new InetSocketAddress("127.0.0.1", 8081);
        InetSocketAddress addr2 = new InetSocketAddress("127.0.0.1", 8082);

        StaticEndpointProvider provider = StaticEndpointProvider.of(addr1, addr2);
        List<InetSocketAddress> endpoints = provider.resolveEndpoints();

        assertEquals(2, endpoints.size());
        assertTrue(endpoints.contains(addr1));
        assertTrue(endpoints.contains(addr2));

        AtomicReference<List<InetSocketAddress>> updated = new AtomicReference<>();
        provider.onUpdate(updated::set);
        assertNotNull(updated.get());
        assertEquals(2, updated.get().size());
    }

    @Test
    void testDnsEndpointProviderLocalhost() {
        try (DnsEndpointProvider provider = DnsEndpointProvider.of("localhost", 9000, 10000)) {
            List<InetSocketAddress> endpoints = provider.resolveEndpoints();
            assertFalse(endpoints.isEmpty(), "localhost should resolve to at least 1 address");
            assertEquals(9000, endpoints.get(0).getPort());
        }
    }
}
