package dev.sweety.versioning.server.data

import dev.sweety.util.signature.Watermark

open class PatchDefinition(
    open val fields: Map<String, Any>,
    open val watermarks: List<Watermark>,
    open val manifestAttributes: Map<String, String>,
    open val targetClass: String
) {
    fun fields(): Map<String, Any> = fields
    fun watermarks(): List<Watermark> = watermarks
    fun manifestAttributes(): Map<String, String> = manifestAttributes
    fun targetClass(): String = targetClass

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PatchDefinition) return false
        return fields == other.fields &&
                watermarks == other.watermarks &&
                manifestAttributes == other.manifestAttributes &&
                targetClass == other.targetClass
    }

    override fun hashCode(): Int {
        var result = fields.hashCode()
        result = 31 * result + watermarks.hashCode()
        result = 31 * result + manifestAttributes.hashCode()
        result = 31 * result + targetClass.hashCode()
        return result
    }

    override fun toString(): String =
        "PatchDefinition(fields=$fields, watermarks=$watermarks, manifestAttributes=$manifestAttributes, targetClass='$targetClass')"
}
