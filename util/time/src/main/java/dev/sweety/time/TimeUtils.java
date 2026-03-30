package dev.sweety.time;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public final class TimeUtils {

    private TimeUtils() {
    }

    public static String date(long millis, String pattern) {
        return new SimpleDateFormat(pattern).format(new Date(millis));
    }

    public static String formatDuration(long millis) {
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        return time(days, hours, minutes, seconds);
    }

    private static String time(long days, long hours, long minutes, long seconds) {
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(String.format("%02dd ", days));
        if (hours > 0 || !sb.isEmpty()) sb.append(String.format("%02dh ", hours));
        if (minutes > 0 || !sb.isEmpty()) sb.append(String.format("%02dm ", minutes));
        sb.append(String.format("%02ds", seconds));
        return sb.toString().trim();
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    public static void sleep(long time, TimeUnit timeUnit) {
        try {
            timeUnit.sleep(time);
        } catch (InterruptedException ignored) {
        }
    }
}
