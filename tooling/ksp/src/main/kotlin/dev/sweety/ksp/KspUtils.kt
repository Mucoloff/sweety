package dev.sweety.ksp

import com.google.devtools.ksp.symbol.*
import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeName

internal fun KSClassDeclaration.annotation(fqn: String) =
    annotations.find { it.annotationType.resolve().declaration.qualifiedName?.asString() == fqn }

internal fun KSFunctionDeclaration.annotation(fqn: String) =
    annotations.find { it.annotationType.resolve().declaration.qualifiedName?.asString() == fqn }

@Suppress("UNCHECKED_CAST")
internal inline fun <reified T> KSAnnotation.arg(name: String): T? =
    arguments.find { it.name?.asString() == name }?.value as? T

internal fun KSAnnotation?.classListArg(name: String): List<AnnotationSpec> {
    this ?: return emptyList()
    @Suppress("UNCHECKED_CAST")
    val types = arguments.find { it.name?.asString() == name }?.value as? List<KSType> ?: return emptyList()
    return types.map { t ->
        AnnotationSpec.builder(ClassName.get(t.declaration.packageName.asString(), t.declaration.simpleName.asString())).build()
    }
}

internal fun KSAnnotation.kstypeArg(name: String): KSType? =
    arguments.find { it.name?.asString() == name }?.value as? KSType

internal fun KSType.implementsIface(fqn: String): Boolean {
    val decl = declaration as? KSClassDeclaration ?: return false
    return decl.superTypes.any { it.resolve().declaration.qualifiedName?.asString() == fqn }
}

internal fun KSType.toJavaPoet(): TypeName {
    val qName = declaration.qualifiedName?.asString() ?: return ClassName.OBJECT
    return when (qName) {
        "kotlin.Int"          -> TypeName.INT
        "kotlin.Long"         -> TypeName.LONG
        "kotlin.Boolean"      -> TypeName.BOOLEAN
        "kotlin.Float"        -> TypeName.FLOAT
        "kotlin.Short"        -> TypeName.SHORT
        "kotlin.Byte"         -> TypeName.BYTE
        "kotlin.Double"       -> TypeName.DOUBLE
        "kotlin.Char"         -> TypeName.CHAR
        "kotlin.String"       -> ClassName.get(String::class.java)
        "kotlin.IntArray"     -> ArrayTypeName.of(TypeName.INT)
        "kotlin.LongArray"    -> ArrayTypeName.of(TypeName.LONG)
        "kotlin.BooleanArray" -> ArrayTypeName.of(TypeName.BOOLEAN)
        "kotlin.FloatArray"   -> ArrayTypeName.of(TypeName.FLOAT)
        "kotlin.ShortArray"   -> ArrayTypeName.of(TypeName.SHORT)
        "kotlin.ByteArray"    -> ArrayTypeName.of(TypeName.BYTE)
        "kotlin.DoubleArray"  -> ArrayTypeName.of(TypeName.DOUBLE)
        "kotlin.CharArray"    -> ArrayTypeName.of(TypeName.CHAR)
        "kotlin.Array"        -> {
            val arg = arguments.firstOrNull()?.type?.resolve()
            val comp = arg?.toJavaPoet() ?: ClassName.OBJECT
            ArrayTypeName.of(if (comp.isPrimitive) comp.box() else comp)
        }
        "kotlin.Unit"         -> TypeName.VOID
        else -> ClassName.get(declaration.packageName.asString(), declaration.simpleName.asString())
    }
}
