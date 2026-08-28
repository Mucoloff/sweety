package dev.sweety.file

import dev.sweety.data.buffer.BufferPool
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ArchiveUtils {

    private const val ZIP_THRESHOLD = 64 * 1024 // 64 KB

    // ==================================================================================
    // GZIP OPERATIONS
    // ==================================================================================

    @JvmStatic
    fun compressGzip(data: ByteArray, signature: ByteArray?): ByteArray {
        val baos = ByteArrayOutputStream()
        if (signature != null && signature.isNotEmpty()) {
            baos.write(signature)
        }
        GZIPOutputStream(baos).use { gos ->
            gos.write(data)
            gos.finish()
        }
        return baos.toByteArray()
    }

    @JvmStatic
    fun decompressGzip(data: ByteArray, signature: ByteArray?): ByteArray {
        var startOffset = 0
        if (signature != null && signature.isNotEmpty()) {
            if (data.size < signature.size) {
                throw IOException("Data too short for compressed format")
            }
            if (!data.copyOfRange(0, signature.size).contentEquals(signature)) {
                return data
            }
            startOffset = signature.size
        }
        ByteArrayInputStream(data, startOffset, data.size - startOffset).use { input ->
            GZIPInputStream(input).use { gis -> return gis.readAllBytes() }
        }
    }

    @JvmStatic
    fun isGzipCompressed(data: ByteArray, signature: ByteArray?): Boolean {
        if (signature == null || signature.isEmpty()) return false
        if (data.size < signature.size) return false
        return data.copyOfRange(0, signature.size).contentEquals(signature)
    }

    // ==================================================================================
    // ZIP OPERATIONS
    // ==================================================================================

    @JvmStatic
    @Throws(IOException::class)
    fun zipSmart(path: Path): ByteArray {
        if (Files.isDirectory(path)) return zipDirectory(path)
        if (Files.size(path) > ZIP_THRESHOLD) return zipFile(path)
        return Files.readAllBytes(path)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun zipFile(file: Path): ByteArray {
        val baos = ByteArrayOutputStream()
        createZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry(file.fileName.toString()))
            copyWithScratch(file, zos)
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun zipDirectory(root: Path): ByteArray {
        val baos = ByteArrayOutputStream()
        createZipOutputStream(baos).use { zos ->
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(d: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (d != root) {
                        val entryName = root.relativize(d).toString().replace('\\', '/') + "/"
                        zos.putNextEntry(ZipEntry(entryName))
                        zos.closeEntry()
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(f: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val entryName = root.relativize(f).toString().replace('\\', '/')
                    zos.putNextEntry(ZipEntry(entryName))
                    copyWithScratch(f, zos)
                    zos.closeEntry()
                    return FileVisitResult.CONTINUE
                }
            })
        }
        return baos.toByteArray()
    }

    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun zipBytes(data: ByteArray, entryName: String, length: Int = data.size): ByteArray {
        val baos = ByteArrayOutputStream(length + 64)
        createZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry(entryName))
            zos.write(data, 0, length)
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun unzipFirstFile(zipData: ByteArray, length: Int = zipData.size): ByteArray {
        ZipInputStream(ByteArrayInputStream(zipData, 0, length)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    return zis.readAllBytes()
                }
                entry = zis.nextEntry
            }
        }
        throw IOException("ZIP contains no file entries")
    }

    @JvmStatic
    @Throws(IOException::class)
    fun unzip(zipData: ByteArray, outputDir: Path): Path {
        Files.createDirectories(outputDir)
        val targetDir = outputDir.toAbsolutePath().normalize()

        ZipInputStream(ByteArrayInputStream(zipData)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name

                if (entryName.isBlank() || entryName == "/" || entryName == "\\") {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }

                val targetFile = outputDir.resolve(entryName).normalize()
                val absTarget = targetFile.toAbsolutePath().normalize()

                if (!absTarget.startsWith(targetDir)) {
                    throw SecurityException("Invalid zip entry (Path Traversal attempt): $entryName")
                }

                if (entry.isDirectory) {
                    Files.createDirectories(absTarget)
                } else {
                    val parent = absTarget.parent
                    if (parent != null) Files.createDirectories(parent)
                    BufferedOutputStream(Files.newOutputStream(absTarget)).use { os -> transferWithScratch(zis, os) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return outputDir
    }

    // ==================================================================================
    // INTERNAL HELPERS
    // ==================================================================================

    private fun createZipOutputStream(out: OutputStream): ZipOutputStream {
        val zos = ZipOutputStream(BufferedOutputStream(out))
        zos.setLevel(Deflater.BEST_COMPRESSION)
        return zos
    }

    private fun copyWithScratch(src: Path, dst: OutputStream) {
        val scratch = BufferPool.DEFAULT.borrowBytes(16384)
        try {
            Files.newInputStream(src).use { input ->
                var n = input.read(scratch)
                while (n > 0) {
                    dst.write(scratch, 0, n)
                    n = input.read(scratch)
                }
            }
        } finally {
            BufferPool.DEFAULT.returnBytes(scratch)
        }
    }

    private fun transferWithScratch(src: InputStream, dst: OutputStream) {
        val scratch = BufferPool.DEFAULT.borrowBytes(16384)
        try {
            var n = src.read(scratch)
            while (n > 0) {
                dst.write(scratch, 0, n)
                n = src.read(scratch)
            }
        } finally {
            BufferPool.DEFAULT.returnBytes(scratch)
        }
    }
}
