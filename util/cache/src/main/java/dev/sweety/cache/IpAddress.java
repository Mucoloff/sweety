package dev.sweety.cache;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.data.buffer.io.AbstractCodec;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Binary numeric IP address value type for high-performance caches, rate limiters, and Netty codecs.
 *
 * <p><b>CRITICAL DESIGN CONTRACT:</b>
 * The underlying {@link #bytes} field holds the <b>raw numeric network-order bytes</b> of the IP:
 * <ul>
 *   <li><b>IPv4:</b> Exactly 4 bytes ({@code byte[4]}) representing {@code [b0, b1, b2, b3]}, which can also
 *       be encoded as / converted to a 32-bit signed/unsigned integer (see {@link #fromIpv4(int)} and {@link #toIpv4Int()}).</li>
 *   <li><b>IPv6:</b> Exactly 16 bytes ({@code byte[16]}) representing {@code [b0, ..., b15]}.</li>
 * </ul>
 *
 * <p><b>WARNING:</b> {@link #bytes} is <b>NEVER</b> the UTF-8/ASCII character bytes of an IP string (e.g.
 * do <i>NOT</i> pass {@code "127.0.0.1".getBytes(StandardCharsets.UTF_8)}). To instantiate an {@link IpAddress}
 * from a string literal, always use {@link #parse(String)} or {@link #from(InetAddress)}.
 */
public class IpAddress implements AbstractCodec {

    private byte[] bytes;
    private String address;

    private IpAddress(byte[] bytes, String address) {
        this.bytes = bytes != null ? bytes : new byte[0];
        this.address = address != null ? address : "";
    }

    public IpAddress() {
        this(new byte[0], "");
    }

    /**
     * Creates an {@link IpAddress} from raw numeric network-order bytes and optional cached textual representation.
     *
     * @param bytes   4 numeric bytes for IPv4, 16 numeric bytes for IPv6 (NOT UTF-8 string bytes!)
     * @param address canonical text representation (e.g. "127.0.0.1")
     */
    public static IpAddress of(byte[] bytes, String address) {
        return new IpAddress(bytes, address);
    }

    /**
     * Creates an IPv4 {@link IpAddress} directly from a 32-bit big-endian integer.
     *
     * @param ipInt 32-bit integer encoding the 4 octets: {@code (b0 << 24) | (b1 << 16) | (b2 << 8) | b3}
     */
    public static IpAddress fromIpv4(int ipInt) {
        byte[] bytes = new byte[] {
                (byte) ((ipInt >>> 24) & 0xFF),
                (byte) ((ipInt >>> 16) & 0xFF),
                (byte) ((ipInt >>> 8) & 0xFF),
                (byte) (ipInt & 0xFF)
        };
        String addr = (bytes[0] & 0xFF) + "." + (bytes[1] & 0xFF) + "." + (bytes[2] & 0xFF) + "." + (bytes[3] & 0xFF);
        return new IpAddress(bytes, addr);
    }

    /**
     * Creates an IPv4 {@link IpAddress} directly from 4 numeric octets.
     */
    public static IpAddress fromIpv4(byte b0, byte b1, byte b2, byte b3) {
        byte[] bytes = new byte[] { b0, b1, b2, b3 };
        String addr = (b0 & 0xFF) + "." + (b1 & 0xFF) + "." + (b2 & 0xFF) + "." + (b3 & 0xFF);
        return new IpAddress(bytes, addr);
    }

    /**
     * Parses a textual IPv4 or IPv6 literal into its binary numeric representation without blocking DNS resolution.
     *
     * @param ip textual IP literal (e.g. "192.168.1.1", "127.0.0.1", "::1")
     * @return parsed {@link IpAddress}, or blank instance if null/blank
     */
    public static IpAddress parse(String ip) {
        if (ip == null || ip.isBlank()) return new IpAddress();
        String trimmed = ip.trim();

        // Fast zero-DNS IPv4 parser (O(N) single-pass)
        byte[] v4 = parseIpv4Literal(trimmed);
        if (v4 != null) {
            return new IpAddress(v4, trimmed);
        }

        // IPv6 or fallback via InetAddress (JVM does not perform DNS lookup for numeric literals)
        try {
            InetAddress inet = InetAddress.getByName(trimmed);
            return new IpAddress(inet.getAddress(), inet.getHostAddress());
        } catch (UnknownHostException e) {
            // Unparseable literal
            return new IpAddress(new byte[0], trimmed);
        }
    }

    private static byte[] parseIpv4Literal(String s) {
        int len = s.length();
        if (len < 7 || len > 15) return null;
        byte[] bytes = new byte[4];
        int octet = 0;
        int count = 0;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c == '.') {
                if (count == 3 || i == 0 || s.charAt(i - 1) == '.') return null;
                bytes[count++] = (byte) octet;
                octet = 0;
            } else if (c >= '0' && c <= '9') {
                octet = octet * 10 + (c - '0');
                if (octet > 255) return null;
            } else {
                return null;
            }
        }
        if (count != 3 || s.charAt(len - 1) == '.') return null;
        bytes[3] = (byte) octet;
        return bytes;
    }

    public static IpAddress from(InetAddress inet) {
        if (inet == null) return new IpAddress();
        return new IpAddress(inet.getAddress(), inet.getHostAddress());
    }

    public static IpAddress from(java.net.InetSocketAddress socketAddress) {
        if (socketAddress == null) return new IpAddress();
        return from(socketAddress.getAddress() != null ? socketAddress.getAddress() : null);
    }

    public boolean isIpv4() {
        return bytes.length == 4;
    }

    public boolean isIpv6() {
        return bytes.length == 16;
    }

    /**
     * Converts an IPv4 address to its 32-bit big-endian integer representation.
     *
     * @throws IllegalStateException if this is not a 4-byte IPv4 address
     */
    public int toIpv4Int() {
        if (bytes.length != 4) {
            throw new IllegalStateException("Not an IPv4 address (length=" + bytes.length + ")");
        }
        return ((bytes[0] & 0xFF) << 24)
                | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8)
                | (bytes[3] & 0xFF);
    }

    public java.net.InetSocketAddress toInetSocketAddress(int port) {
        return new java.net.InetSocketAddress(address, port);
    }

    public InetAddress toInetAddress() throws UnknownHostException {
        return InetAddress.getByAddress(bytes);
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public String getAddress() {
        return address;
    }

    public boolean isBlank() {
        return address.isBlank();
    }

    @Override
    public String toString() {
        return address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IpAddress ipAddress = (IpAddress) o;
        return Arrays.equals(bytes, ipAddress.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public void write(BufferWriter buffer) {
        buffer.writeByteArray(bytes);
    }

    @Override
    public void read(BufferReader buffer) {
        this.bytes = buffer.readByteArray();
        try {
            this.address = InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException e) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bytes.length; i++) {
                if (i > 0) sb.append('.');
                sb.append(bytes[i] & 0xFF);
            }
            this.address = sb.toString();
        }
    }
}
