package dev.sweety.util.system;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Locale;

public enum OperatingSystem {
    LINUX("linux"),
    SOLARIS("solaris"),
    WINDOWS("windows") {
        protected String[] getURLOpenCommand(URL url) {
            return new String[]{"rundll32", "url.dll,FileProtocolHandler", url.toString()};
        }
    },
    OSX("mac") {
        protected String[] getURLOpenCommand(URL url) {
            return new String[]{"open", url.toString()};
        }
    },
    UNKNOWN("unknown");

    public static final OperatingSystem[] VALUES = values();

    private static final Logger LOGGER = LoggerFactory.getLogger(OperatingSystem.class);

    private final String name;

    public @NotNull String getName() {
        return name;
    }

    OperatingSystem(String name) {
        this.name = name;
    }

    private static OperatingSystem CACHE;

    public static OperatingSystem os() {
        if (CACHE == null) CACHE = detectOS();
        return CACHE;
    }

    public boolean isThis() {
        return os() == this;
    }

    public static OperatingSystem detectOS() {
        String os = System.getProperty("os.name", "generic").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return OperatingSystem.WINDOWS;
        if (os.contains("mac")) return OperatingSystem.OSX;
        if (os.contains("solaris") || os.contains("sunos")) return OperatingSystem.SOLARIS;
        if (os.contains("linux") || os.contains("unix")) return OperatingSystem.LINUX;
        return OperatingSystem.UNKNOWN;
    }

    public void open(URL url) {
        try {
            Process process = Runtime.getRuntime().exec(this.getURLOpenCommand(url));
            process.getInputStream().close();
            process.getErrorStream().close();
            process.getOutputStream().close();
        } catch (IOException exception) {
            LOGGER.error("Couldn't open url {}", url, exception);
        }

    }

    public void open(URI uri) {
        try {
            this.open(uri.toURL());
        } catch (MalformedURLException exception) {
            LOGGER.error("Couldn't open uri {}", uri, exception);
        }

    }

    public void open(Path path) {
        try {
            this.open(path.toUri().toURL());
        } catch (MalformedURLException exception) {
            LOGGER.error("Couldn't open file {}", path, exception);
        }

    }

    protected String[] getURLOpenCommand(URL url) {
        String string = url.toString();
        if ("file".equals(url.getProtocol())) string = string.replace("file:", "file://");

        return new String[]{"xdg-open", string};
    }

    public void open(String uri) {
        try {
            this.open((new URI(uri)).toURL());
        } catch (MalformedURLException | IllegalArgumentException | URISyntaxException exception) {
            LOGGER.error("Couldn't open uri {}", uri, exception);
        }

    }

}
