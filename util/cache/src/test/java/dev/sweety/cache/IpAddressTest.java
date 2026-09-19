package dev.sweety.cache;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

class IpAddressTest {

    @Test
    void testIpv4ParsingAndNumericConversion() {
        IpAddress ip = IpAddress.parse("192.168.1.1");
        assertNotNull(ip);
        assertTrue(ip.isIpv4());
        assertFalse(ip.isIpv6());
        assertEquals("192.168.1.1", ip.getAddress());

        byte[] expectedBytes = new byte[] {(byte) 192, (byte) 168, 1, 1};
        assertArrayEquals(expectedBytes, ip.getBytes());

        // Verify integer conversion
        int ipInt = ip.toIpv4Int();
        IpAddress fromInt = IpAddress.fromIpv4(ipInt);
        assertEquals(ip, fromInt);
        assertArrayEquals(expectedBytes, fromInt.getBytes());
        assertEquals("192.168.1.1", fromInt.getAddress());
    }

    @Test
    void testIpv4FromExplicitBytes() {
        IpAddress ip = IpAddress.fromIpv4((byte) 10, (byte) 0, (byte) 0, (byte) 254);
        assertTrue(ip.isIpv4());
        assertEquals("10.0.0.254", ip.getAddress());
        assertEquals(new byte[] {10, 0, 0, (byte) 254}[3], ip.getBytes()[3]);
    }

    @Test
    void testIpv6Parsing() {
        IpAddress ip = IpAddress.parse("::1");
        assertNotNull(ip);
        assertTrue(ip.isIpv6());
        assertFalse(ip.isIpv4());
        assertEquals(16, ip.getBytes().length);
        assertThrows(IllegalStateException.class, ip::toIpv4Int);
    }

    @Test
    void testEqualityMatchesInetAddress() throws UnknownHostException {
        InetAddress inet = InetAddress.getByName("127.0.0.1");
        IpAddress fromInet = IpAddress.from(inet);
        IpAddress fromParse = IpAddress.parse("127.0.0.1");
        IpAddress fromInt = IpAddress.fromIpv4((127 << 24) | 1);

        assertEquals(fromInet, fromParse);
        assertEquals(fromInet.hashCode(), fromParse.hashCode());
        assertEquals(fromInet, fromInt);
    }

    @Test
    void testBlankAndNullHandling() {
        assertTrue(IpAddress.parse(null).isBlank());
        assertTrue(IpAddress.parse("").isBlank());
        assertTrue(IpAddress.parse("   ").isBlank());
    }
}
