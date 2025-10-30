package dev.sweety.core.system;

import lombok.experimental.UtilityClass;

import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Locale;

@UtilityClass
public class SystemUtils {

    public String[] getHwid() {
        return new String[]{
            System.getProperty("sun.arch.data.model"),
            String.valueOf(Runtime.getRuntime().availableProcessors()),
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
            System.getProperty("os.version"),
            System.getProperty("java.version"),
            System.getProperty("java.vendor"),
            System.getProperty("java.vendor.url"),
            System.getProperty("java.home"),
            System.getenv("NUMBER_OF_PROCESSORS"),
            System.getenv("PROCESSOR_LEVEL"),
            System.getenv("PROCESSOR_REVISION"),
            getMAC()
        };
    }

    private String getMAC() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                byte[] mac = ni.getHardwareAddress();
                if (mac != null) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : mac) sb.append(String.format("%02X", b));
                    return sb.toString();
                }
            }
        } catch (Exception ignored) {}
        return "UNKNOWN";
    }

    public OperatingSystem detectOS() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return OperatingSystem.WINDOWS;
        if (os.contains("mac")) return OperatingSystem.OSX;
        if (os.contains("solaris") || os.contains("sunos")) return OperatingSystem.SOLARIS;
        if (os.contains("linux") || os.contains("unix")) return OperatingSystem.LINUX;
        return OperatingSystem.UNKNOWN;
    }
}
