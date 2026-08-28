package dev.sweety.data.compress

import java.nio.ByteBuffer
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

object CompressUtils {

    /**
     * Deflates `src[0..srcLen)` into `dst[0..)`.
     * Returns the number of bytes written, or -1 if `dst` is too small.
     */
    @JvmStatic
    fun deflate(src: ByteArray, srcLen: Int, dst: ByteArray, deflater: Deflater): Int {
        deflater.setInput(src, 0, srcLen)
        deflater.finish()
        var written = 0
        while (!deflater.finished() && written < dst.size) {
            written += deflater.deflate(dst, written, dst.size - written)
        }
        return if (deflater.finished()) written else -1
    }

    /**
     * Inflates `src[0..srcLen)` into `dst[0..dstLen)`.
     * `dstLen` must equal the original uncompressed length.
     * Returns the number of bytes written.
     */
    @JvmStatic
    @Throws(DataFormatException::class)
    fun inflate(src: ByteArray, srcLen: Int, dst: ByteArray, dstLen: Int, inflater: Inflater): Int {
        inflater.setInput(src, 0, srcLen)
        return inflater.inflate(dst, 0, dstLen)
    }

    /**
     * Deflates `src` (position → limit) into `dst` (position → limit).
     * Advances both buffers' positions. Returns bytes written, or -1 if dst too small.
     * Uses [Deflater.setInput] / [Deflater.deflate] (JDK 11+),
     * avoiding a heap `byte[]` copy when src is a zero-copy NIO view.
     */
    @JvmStatic
    fun deflate(src: ByteBuffer, dst: ByteBuffer, deflater: Deflater): Int {
        deflater.setInput(src)
        deflater.finish()
        var written = 0
        while (!deflater.finished() && dst.hasRemaining()) {
            written += deflater.deflate(dst)
        }
        return if (deflater.finished()) written else -1
    }

    /**
     * Inflates `src` (position → limit) into `dst` (position → limit).
     * Returns bytes written.
     */
    @JvmStatic
    @Throws(DataFormatException::class)
    fun inflate(src: ByteBuffer, dst: ByteBuffer, inflater: Inflater): Int {
        inflater.setInput(src)
        return inflater.inflate(dst)
    }

    private const val CHUNK = 1 shl 16

    /**
     * Inflates `src` into `dst[0..dstLen)` in bounded chunks, guarding against zip-bomb style
     * inputs: aborts once `maxNanos` wall-clock time elapses (CPU-bomb) or once produced bytes
     * exceed `maxOutputBytes` (memory-bomb, belt-and-braces alongside any caller-side size checks).
     */
    @JvmStatic
    @Throws(DataFormatException::class, CompressionLimitException::class)
    fun inflateBounded(src: ByteBuffer, dst: ByteArray, dstLen: Int, inflater: Inflater, maxNanos: Long, maxOutputBytes: Int): Int {
        inflater.setInput(src)
        val deadline = System.nanoTime() + maxNanos
        var produced = 0
        while (produced < dstLen && !inflater.finished()) {
            if (System.nanoTime() > deadline) throw CompressionLimitException("Decompression timed out")
            val step = minOf(CHUNK, dstLen - produced)
            val n = inflater.inflate(dst, produced, step)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            produced += n
            if (produced > maxOutputBytes) throw CompressionLimitException("Decompressed output exceeded limit")
        }
        return produced
    }

    /**
     * Deflates `src` into `dst` (position → limit), guarding against pathological/attacker-influenced
     * input that stalls the deflater: aborts once `maxNanos` wall-clock time elapses.
     * Returns bytes written, or -1 if `dst` is too small (same contract as [deflate]).
     */
    @JvmStatic
    @Throws(CompressionLimitException::class)
    fun deflateBounded(src: ByteBuffer, dst: ByteBuffer, deflater: Deflater, maxNanos: Long): Int {
        deflater.setInput(src)
        deflater.finish()
        val deadline = System.nanoTime() + maxNanos
        var written = 0
        while (!deflater.finished() && dst.hasRemaining()) {
            if (System.nanoTime() > deadline) throw CompressionLimitException("Compression timed out")
            written += deflater.deflate(dst)
        }
        return if (deflater.finished()) written else -1
    }
}
