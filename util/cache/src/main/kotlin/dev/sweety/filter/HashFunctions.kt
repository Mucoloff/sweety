package dev.sweety.filter

import java.util.Arrays
import java.util.Objects
import kotlin.xor

/**
 * Fabbriche per array di hash da usare con [CountMinSketch] e [FastScalableCountingBloomFilter].
 */
object HashFunctions {

    private const val DEFAULT_BASE: Int = 0x12345678
    private const val HASH_SPREAD: Int = -0x61c88647 // 0x9E3779B9 as Int (two's complement)

    /**
     * `count` funzioni MurmurHash3 con seed distinti (adatto come default per sketch / bloom).
     */
    @JvmStatic
    fun murmur3Defaults(count: Int): Array<HashFunction> {
        require(count > 0) { "count must be positive: $count" }
        return Array(count) { i -> MurmurHasher(DEFAULT_BASE xor (i * HASH_SPREAD)) }
    }

    /**
     * Copia difensiva; rifiuta elementi `null`.
     */
    @JvmStatic
    fun copy(hashers: Array<HashFunction>?): Array<HashFunction> {
        requireNotNull(hashers) { "hashFunctions array is null" }
        require(hashers.isNotEmpty()) { "at least one HashFunction is required" }
        val out = Arrays.copyOf(hashers, hashers.size)
        for (i in out.indices) {
            requireNotNull(out[i]) { "HashFunction at index $i is null" }
        }
        return out
    }

    /**
     * Come [copy], ma da [Collection] (lista, `Set`, ecc.).
     *
     * L'ordine delle funzioni è quello dell'iteratore della collezione ([java.util.HashSet], ad es.,
     * non garantisce alcun ordine stabile).
     */
    @JvmStatic
    fun copy(hashFunctions: Collection<HashFunction>?): Array<HashFunction> {
        requireNotNull(hashFunctions) { "hashFunctions collection is null" }
        val out = hashFunctions.toTypedArray()
        require(out.isNotEmpty()) { "at least one HashFunction is required" }
        for (i in out.indices) {
            requireNotNull(out[i]) { "HashFunction at index $i is null" }
        }
        return Arrays.copyOf(out, out.size)
    }

    /**
     * Un indice nel range `[0, bucketCount)` per ogni funzione ([Math.floorMod]).
     * Usato internamente da [CountMinSketch] e [FastScalableCountingBloomFilter].
     */
    @JvmStatic
    fun bucketIndices(data: ByteArray?, hashers: Array<HashFunction>?, bucketCount: Int): IntArray {
        Objects.requireNonNull(data, "data")
        Objects.requireNonNull(hashers, "hashers")
        require(!hashers!!.isEmpty()) { "at least one HashFunction is required" }
        require(bucketCount > 0) { "bucketCount must be positive: $bucketCount" }
        val out = IntArray(hashers.size)
        for (i in hashers.indices) {
            val hf = hashers[i]
            requireNotNull(hf) { "HashFunction at index $i is null" }
            out[i] = Math.floorMod(hf.hash(data), bucketCount)
        }
        return out
    }
}