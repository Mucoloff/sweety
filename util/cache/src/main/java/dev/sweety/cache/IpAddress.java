package dev.sweety.cache;

import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.data.buffer.io.AbstractCodec;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

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

    public static IpAddress of(byte[] bytes, String address) {
        return new IpAddress(bytes, address);
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
