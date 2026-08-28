package dev.sweety.versioning.server.util.http

import com.sun.net.httpserver.HttpExchange
import dev.sweety.data.ChecksumUtils
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HttpUtils {

    @JvmStatic
    fun parseQuery(rawQuery: String?): Map<String, String> {
        val out = HashMap<String, String>()
        if (rawQuery == null || rawQuery.isBlank()) {
            return out
        }
        for (pair in rawQuery.split("&")) {
            if (pair.isBlank()) continue
            val idx = pair.indexOf('=')
            if (idx < 0) {
                out[urlDecode(pair)] = ""
            } else {
                out[urlDecode(pair.substring(0, idx))] = urlDecode(pair.substring(idx + 1))
            }
        }
        return out
    }

    @JvmStatic
    fun urlDecode(s: String): String = URLDecoder.decode(s, StandardCharsets.UTF_8)

    @JvmStatic
    @Throws(IOException::class)
    fun sendJson(exchange: HttpExchange, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun sendText(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @JvmStatic
    fun constantTimeEquals(a: String?, b: String?): Boolean {
        if (a == null || b == null || a.length != b.length) {
            return false
        }
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }

    @JvmStatic
    fun verifySignature(secret: String?, signature: String?, body: ByteArray): Boolean {
        if (secret == null || signature == null) return false
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            val digest = mac.doFinal(body)
            val expected = ChecksumUtils.bytesToHex(digest)
            constantTimeEquals(expected, signature)
        } catch (_: Exception) {
            false
        }
    }

    @JvmStatic
    fun extractName(header: String): String? {
        val i = header.indexOf("name=\"")
        if (i < 0) return null
        val start = i + 6
        val end = header.indexOf('"', start)
        return header.substring(start, end)
    }

    @JvmStatic
    fun extractFilename(header: String): String? {
        val i = header.indexOf("filename=\"")
        if (i < 0) return null
        val start = i + 10
        val end = header.indexOf('"', start)
        return header.substring(start, end)
    }
}
