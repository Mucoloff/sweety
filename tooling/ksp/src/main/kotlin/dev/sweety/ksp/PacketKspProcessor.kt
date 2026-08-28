package dev.sweety.ksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.javapoet.*
import dev.sweety.netty.packet.model.Packet
import javax.lang.model.element.Modifier

private const val BUILD_PACKET   = "dev.sweety.packet.processor.BuildPacket"
private const val FIELD_BUFFER   = "dev.sweety.packet.processor.FieldBuffer"
private const val BUFFER_UTILS   = "dev.sweety.packet.processor.BufferUtils"
private const val ABS_ENCODER    = "dev.sweety.data.buffer.io.AbstractEncoder"
private const val ABS_DECODER    = "dev.sweety.data.buffer.io.AbstractDecoder"

class PacketKspProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        resolver.getSymbolsWithAnnotation(BUILD_PACKET).forEach { sym ->
            if (sym is KSClassDeclaration && sym.classKind == ClassKind.INTERFACE) {
                if (sym.validate()) generatePacket(sym)
                else deferred += sym
            }
        }
        return deferred
    }

    private fun generatePacket(decl: KSClassDeclaration) {
        val ann = decl.annotation(BUILD_PACKET)
        val interfaceName = decl.simpleName.asString()
        val pkg = decl.packageName.asString()

        val customName = ann?.arg<String>("name") ?: ""
        val path       = ann?.arg<String>("path") ?: ".packet"
        val packetName = if (customName.isNotEmpty()) customName else "${interfaceName}Packet"
        val packetPkg  = pkg + path

        val ifaceClass = ClassName.get(pkg, interfaceName)

        val writeCtorBuilder = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC)
        val readCtorBuilder  = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(TypeName.INT,                    "_id",        Modifier.FINAL)
            .addParameter(TypeName.LONG,                   "_timestamp", Modifier.FINAL)
            .addParameter(ArrayTypeName.of(TypeName.BYTE), "_data",      Modifier.FINAL)
            .addStatement("super(_id, _timestamp, _data)")

        val classBuilder = TypeSpec.classBuilder(packetName)
            .addModifiers(Modifier.PUBLIC)
            .superclass(ClassName.get(Packet::class.java))
            .addSuperinterface(ifaceClass)

        for (func in decl.getDeclaredFunctions()) {
            val originalName = func.simpleName.asString()
            val fieldAnn     = func.annotation(BUILD_PACKET)
            val fieldCustom  = fieldAnn?.arg<String>("name") ?: ""
            val addMethod    = fieldAnn?.arg<Boolean>("addMethod") ?: false
            val fieldName    = if (fieldCustom.isNotEmpty()) fieldCustom else originalName
            val replace      = fieldCustom.isNotEmpty() && addMethod

            val ksType     = func.returnType?.resolve() ?: continue
            val typeName   = ksType.toJavaPoet()

            val fieldAnnotations = fieldAnn.classListArg("annotations")
            val fieldBuilder = FieldSpec.builder(typeName, fieldName, Modifier.PRIVATE)
            fieldAnnotations.forEach { fieldBuilder.addAnnotation(it) }
            classBuilder.addField(fieldBuilder.build())

            classBuilder.addMethod(
                MethodSpec.methodBuilder(originalName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override::class.java)
                    .returns(typeName)
                    .addStatement("return this.\$N", fieldName)
                    .build()
            )
            if (originalName != fieldName && replace) {
                classBuilder.addMethod(
                    MethodSpec.methodBuilder(fieldName)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(typeName)
                        .addStatement("return this.\$N", fieldName)
                        .build()
                )
            }

            writeCtorBuilder.addParameter(typeName, fieldName, Modifier.FINAL)

            val fbAnn = func.annotation(FIELD_BUFFER)
            if (fbAnn != null) {
                val encoderType = fbAnn.kstypeArg("encoder")?.toJavaPoet()
                val decoderType = fbAnn.kstypeArg("decoder")?.toJavaPoet()
                if (encoderType != null && decoderType != null) {
                    val bu = ClassName.bestGuess(BUFFER_UTILS)
                    writeCtorBuilder.addStatement("this.buffer().writeObject(\$N, \$T.encoder(\$T.class))", fieldName, bu, encoderType)
                    readCtorBuilder.addStatement("this.\$N = this.buffer().readObject(\$T.decoder(\$T.class))", fieldName, bu, decoderType)
                }
                continue
            }

            generateBuffer(fieldName, ksType, typeName, writeCtorBuilder, readCtorBuilder)
        }

        ann.classListArg("annotations").forEach { classBuilder.addAnnotation(it) }
        classBuilder.addMethod(writeCtorBuilder.build()).addMethod(readCtorBuilder.build())

        val file = codeGenerator.createNewFile(
            Dependencies(false, decl.containingFile!!), packetPkg, packetName, "java"
        )
        file.bufferedWriter().use { JavaFile.builder(packetPkg, classBuilder.build()).build().writeTo(it) }
    }

    private fun generateBuffer(
        fieldName: String, ksType: KSType, typeName: TypeName,
        write: MethodSpec.Builder, read: MethodSpec.Builder,
    ) {
        val qName = ksType.declaration.qualifiedName?.asString() ?: run {
            logger.error("Cannot resolve type for field $fieldName"); return
        }
        when (qName) {
            "kotlin.Boolean"      -> { write.addStatement("this.buffer().writeBoolean(\$N)", fieldName);    read.addStatement("this.\$N = this.buffer().readBoolean()", fieldName) }
            "kotlin.Float"        -> { write.addStatement("this.buffer().writeFloat(\$N)", fieldName);      read.addStatement("this.\$N = this.buffer().readFloat()", fieldName) }
            "kotlin.Short"        -> { write.addStatement("this.buffer().writeShort(\$N)", fieldName);      read.addStatement("this.\$N = this.buffer().readShort()", fieldName) }
            "kotlin.Byte"         -> { write.addStatement("this.buffer().writeByte(\$N)", fieldName);       read.addStatement("this.\$N = this.buffer().readByte()", fieldName) }
            "kotlin.Double"       -> { write.addStatement("this.buffer().writeDouble(\$N)", fieldName);     read.addStatement("this.\$N = this.buffer().readDouble()", fieldName) }
            "kotlin.Char"         -> { write.addStatement("this.buffer().writeChar(\$N)", fieldName);       read.addStatement("this.\$N = this.buffer().readChar()", fieldName) }
            "kotlin.Int"          -> { write.addStatement("this.buffer().writeVarInt(\$N)", fieldName);     read.addStatement("this.\$N = this.buffer().readVarInt()", fieldName) }
            "kotlin.Long"         -> { write.addStatement("this.buffer().writeVarLong(\$N)", fieldName);    read.addStatement("this.\$N = this.buffer().readVarLong()", fieldName) }
            "kotlin.String"       -> { write.addStatement("this.buffer().writeString(\$N)", fieldName);     read.addStatement("this.\$N = this.buffer().readString()", fieldName) }
            "java.util.UUID"      -> { write.addStatement("this.buffer().writeUuid(\$N)", fieldName);       read.addStatement("this.\$N = this.buffer().readUuid()", fieldName) }
            "kotlin.BooleanArray" -> { write.addStatement("this.buffer().writeBooleanArray(\$N)", fieldName); read.addStatement("this.\$N = this.buffer().readBooleanArray()", fieldName) }
            "kotlin.FloatArray"   -> { write.addStatement("this.buffer().writeFloatArray(\$N)", fieldName);   read.addStatement("this.\$N = this.buffer().readFloatArray()", fieldName) }
            "kotlin.ShortArray"   -> { write.addStatement("this.buffer().writeShortArray(\$N)", fieldName);   read.addStatement("this.\$N = this.buffer().readShortArray()", fieldName) }
            "kotlin.ByteArray"    -> { write.addStatement("this.buffer().writeByteArray(\$N)", fieldName);    read.addStatement("this.\$N = this.buffer().readByteArray()", fieldName) }
            "kotlin.DoubleArray"  -> { write.addStatement("this.buffer().writeDoubleArray(\$N)", fieldName);  read.addStatement("this.\$N = this.buffer().readDoubleArray()", fieldName) }
            "kotlin.CharArray"    -> { write.addStatement("this.buffer().writeCharArray(\$N)", fieldName);    read.addStatement("this.\$N = this.buffer().readCharArray()", fieldName) }
            "kotlin.IntArray"     -> { write.addStatement("this.buffer().writeVarIntArray(\$N)", fieldName);  read.addStatement("this.\$N = this.buffer().readVarIntArray()", fieldName) }
            "kotlin.LongArray"    -> { write.addStatement("this.buffer().writeVarLongArray(\$N)", fieldName); read.addStatement("this.\$N = this.buffer().readVarLongArray()", fieldName) }
            "kotlin.Array"        -> handleObjectArray(fieldName, ksType, write, read)
            else                  -> handleDeclaredType(fieldName, ksType, typeName, qName, write, read)
        }
    }

    private fun handleDeclaredType(
        fieldName: String, ksType: KSType, typeName: TypeName, qName: String,
        write: MethodSpec.Builder, read: MethodSpec.Builder,
    ) {
        val decl = ksType.declaration as? KSClassDeclaration ?: run {
            logger.error("Cannot resolve declaration for $qName in field $fieldName"); return
        }
        when {
            decl.classKind == ClassKind.ENUM_CLASS -> {
                write.addStatement("this.buffer().writeEnum(\$N)", fieldName)
                read.addStatement("this.\$N = this.buffer().readEnum(\$T.class)", fieldName, typeName)
            }
            ksType.implementsIface(ABS_ENCODER) && ksType.implementsIface(ABS_DECODER) -> {
                write.addStatement("this.buffer().writeObject(\$N)", fieldName)
                read.addStatement("this.\$N = this.buffer().readObject(\$T::new)", fieldName, typeName)
            }
            else -> logger.error("Unsupported type $qName for field $fieldName. Implement AbstractEncoder+AbstractDecoder or use @FieldBuffer.")
        }
    }

    private fun handleObjectArray(
        fieldName: String, ksType: KSType,
        write: MethodSpec.Builder, read: MethodSpec.Builder,
    ) {
        val componentKsType = ksType.arguments.firstOrNull()?.type?.resolve() ?: run {
            logger.error("Cannot resolve array component for $fieldName"); return
        }
        val componentQName   = componentKsType.declaration.qualifiedName?.asString() ?: return
        val componentTypeName = componentKsType.toJavaPoet()
        val bw = ClassName.get("dev.sweety.data.buffer", "BufferWriter")
        val br = ClassName.get("dev.sweety.data.buffer", "BufferReader")

        when (componentQName) {
            "kotlin.String"  -> { write.addStatement("this.buffer().writeArray(\$T::writeString, \$N)", bw, fieldName);  read.addStatement("this.\$N = this.buffer().readArray(\$T::readString, \$T[]::new)", fieldName, br, ClassName.get(String::class.java)) }
            "java.util.UUID" -> { write.addStatement("this.buffer().writeArray(\$T::writeUuid, \$N)", bw, fieldName);    read.addStatement("this.\$N = this.buffer().readArray(\$T::readUuid, \$T[]::new)", fieldName, br, ClassName.get("java.util", "UUID")) }
            else -> {
                val compDecl = componentKsType.declaration as? KSClassDeclaration
                if (compDecl?.classKind == ClassKind.ENUM_CLASS) {
                    write.addStatement("this.buffer().writeArray(\$T::writeEnum, \$N)", bw, fieldName)
                    read.addStatement("this.\$N = this.buffer().readArray(buffer -> buffer.readEnum(\$T.class), \$T[]::new)", fieldName, componentTypeName, componentTypeName)
                } else {
                    when (componentQName) {
                        "kotlin.Int"     -> { write.addStatement($$"this.buffer().writeArray($T::writeVarInt, $N)", bw, fieldName);   read.addStatement("this.\$N = this.buffer().readArray(\$T::readVarInt, \$T[]::new)", fieldName, br, ClassName.get(Integer::class.java)) }
                        "kotlin.Boolean" -> { write.addStatement($$"this.buffer().writeArray($T::writeBoolean, $N)", bw, fieldName);  read.addStatement("this.\$N = this.buffer().readArray(\$T::readBoolean, \$T[]::new)", fieldName, br, ClassName.get(java.lang.Boolean::class.java)) }
                        "kotlin.Long"    -> { write.addStatement($$"this.buffer().writeArray($T::writeVarLong, $N)", bw, fieldName);  read.addStatement("this.\$N = this.buffer().readArray(\$T::readVarLong, \$T[]::new)", fieldName, br, ClassName.get(java.lang.Long::class.java)) }
                        "kotlin.Short"   -> { write.addStatement($$"this.buffer().writeArray($T::writeShort, $N)", bw, fieldName);   read.addStatement("this.\$N = this.buffer().readArray(\$T::readShort, \$T[]::new)", fieldName, br, ClassName.get(java.lang.Short::class.java)) }
                        "kotlin.Float"   -> { write.addStatement($$"this.buffer().writeArray($T::writeFloat, $N)", bw, fieldName);   read.addStatement("this.\$N = this.buffer().readArray(\$T::readFloat, \$T[]::new)", fieldName, br, ClassName.get(java.lang.Float::class.java)) }
                        "kotlin.Double"  -> { write.addStatement($$"this.buffer().writeArray($T::writeDouble, $N)", bw, fieldName);  read.addStatement("this.\$N = this.buffer().readArray(\$T::readDouble, \$T[]::new)", fieldName, br, ClassName.get(java.lang.Double::class.java)) }
                        "kotlin.Byte"    -> { write.addStatement($$"this.buffer().writeArray($T::writeByte, $N)", bw, fieldName);    read.addStatement("this.\$N = this.buffer().readArray(\$T::readByte, \$T[]::new)", fieldName, br, ClassName.get(java.lang.Byte::class.java)) }
                        else -> logger.error("Unsupported array component $componentQName in field $fieldName")
                    }
                }
            }
        }
    }
}

// ── KSP helpers ──────────────────────────────────────────────────────────────

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
