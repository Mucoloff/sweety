package dev.sweety.core.system;

import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;

@Getter
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

    private final String name;

    OperatingSystem(String name) {
        this.name = name;
    }

    public void open(URL url) {
        try {
            Process process = Runtime.getRuntime().exec(this.getURLOpenCommand(url));
            process.getInputStream().close();
            process.getErrorStream().close();
            process.getOutputStream().close();
        } catch (IOException exception) {
            System.err.printf("Couldn't open url %s %s\n", url, exception);
        }

    }

    public void open(URI uri) {
        try {
            this.open(uri.toURL());
        } catch (MalformedURLException exception) {
            System.err.printf("Couldn't open uri %s %s\n", uri, exception);
        }

    }

    public void open(File file) {
        try {
            this.open(file.toURI().toURL());
        } catch (MalformedURLException exception) {
            System.err.printf("Couldn't open file %s %s\n", file, exception);
        }

    }

    protected String[] getURLOpenCommand(URL url) {
        String string = url.toString();
        if ("file".equals(url.getProtocol())) {
            string = string.replace("file:", "file://");
        }

        return new String[]{"xdg-open", string};
    }

    public void open(String uri) {
        try {
            this.open((new URI(uri)).toURL());
        } catch (MalformedURLException | IllegalArgumentException | URISyntaxException exception) {
            System.err.printf("Couldn't open uri %s %s\n", uri, exception);
        }

    }

    public static OperatingSystem detectOS() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return OperatingSystem.WINDOWS;
        if (os.contains("mac")) return OperatingSystem.OSX;
        if (os.contains("solaris") || os.contains("sunos")) return OperatingSystem.SOLARIS;
        if (os.contains("linux") || os.contains("unix")) return OperatingSystem.LINUX;
        return OperatingSystem.UNKNOWN;
    }

}
