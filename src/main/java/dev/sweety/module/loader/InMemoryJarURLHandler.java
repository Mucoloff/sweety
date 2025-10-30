package dev.sweety.module.loader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URL;

public class InMemoryJarURLHandler extends URLStreamHandler {

    private final byte[] jarBytes;

    public InMemoryJarURLHandler(byte[] jarBytes) {
        this.jarBytes = jarBytes;
    }

    @Override
    protected URLConnection openConnection(URL u) {
        return new URLConnection(u) {
            @Override
            public void connect() {}

            @Override
            public java.io.InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(jarBytes);
            }
        };
    }
}
