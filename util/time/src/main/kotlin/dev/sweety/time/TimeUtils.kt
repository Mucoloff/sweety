package dev.sweety.time

import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

object TimeUtils {

    @JvmStatic
    fun date(millis: Long, pattern: String): String = SimpleDateFormat(pattern).format(Date(millis))

    @JvmStatic
    fun formatDuration(millis: Long): String {
        val days = TimeUnit.MILLISECONDS.toDays(millis)
        val hours = TimeUnit.MILLISECONDS.toHours(millis) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return time(days, hours, minutes, seconds)
    }

    private fun time(days: Long, hours: Long, minutes: Long, seconds: Long): String {
        val sb = StringBuilder()
        if (days > 0) sb.append("%02dd ".format(days))
        if (hours > 0 || sb.isNotEmpty()) sb.append("%02dh ".format(hours))
        if (minutes > 0 || sb.isNotEmpty()) sb.append("%02dm ".format(minutes))
        sb.append("%02ds".format(seconds))
        return sb.toString().trim()
    }

    @JvmStatic
    fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }

    @JvmStatic
    fun sleep(time: Long, timeUnit: TimeUnit) {
        try {
            timeUnit.sleep(time)
        } catch (_: InterruptedException) {
        }
    }
}
