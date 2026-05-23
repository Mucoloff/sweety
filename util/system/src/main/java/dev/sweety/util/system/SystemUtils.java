package dev.sweety.util.system;

import java.net.NetworkInterface;
import java.util.Collections;
import java.util.logging.Logger;

public final class SystemUtils {

    private static final Logger LOG = Logger.getLogger(SystemUtils.class.getName());

    public static String[] getHwid() {
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

    private static String getMAC() {
        try {
            var e = NetworkInterface.getNetworkInterfaces();
            while (e.hasMoreElements()) {
                var ni = e.nextElement();
                byte[] mac = ni.getHardwareAddress();
                if (mac != null) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : mac) sb.append(String.format("%02X", b));
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            LOG.warning("Failed to read MAC address: " + e.getMessage());
        }
        return "UNKNOWN";
    }

}
