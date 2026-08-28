package dev.sweety.patch.format.archive

import dev.sweety.patch.model.PatchOperation

/** Single operation in a patch archive index.  */
data class PatchArchiveOpEntry(
    @JvmField
    var type: PatchOperation.Type, /** "add | "modify" | "delete" | "move"  */
    @JvmField
    var path: String,
    @JvmField
    var hash: String?,
    @JvmField
    var method: PatchOperation.Method
    /** "replacement" | "text_diff"; omitted defaults to replacement  */
) {

    constructor(type: PatchOperation.Type, path: String, hash: String?) : this(type, path, hash, PatchOperation.Method.REPLACEMENT)

    /** old path for MOVE; absent otherwise  */
    @JvmField
    var oldPath: String? = null
    /** Zip entry path under the same archive, e.g. `p/0`; absent for delete  */
    @JvmField
    var payloadEntry: String? = null

    fun copy(): PatchArchiveOpEntry {
        val c = PatchArchiveOpEntry(type, path, hash, method)

        c.oldPath = oldPath
        c.payloadEntry = if (PatchOperation.Type.DELETE == c.type) null else payloadEntry

        return c
    }
}