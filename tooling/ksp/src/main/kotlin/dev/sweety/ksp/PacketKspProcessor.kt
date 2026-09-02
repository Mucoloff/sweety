package dev.sweety.ksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.javapoet.*
import dev.sweety.data.buffer.BufferReader
import dev.sweety.data.buffer.BufferWriter
import dev.sweety.math.pool.ObjectPool
import dev.sweety.netty.packet.model.Packet
import javax.lang.model.element.Modifier

private const val BUILD_PACKET   = "dev.sweety.packet.processor.BuildPacket"
private const val FIELD_BUFFER   = "dev.sweety.packet.processor.FieldBuffer"
private const val BUFFER_UTILS   = "dev.sweety.packet.processor.BufferUtils"
private const val ABS_ENCODER    = "dev.sweety.data.buffer.io.AbstractEncoder"
private const val ABS_DECODER    = "dev.sweety.data.buffer.io.AbstractDecoder"
private const val IGNORE_ANN     = "dev.sweety.processor.Ignore"

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

        var cleanName = interfaceName
        if (cleanName.endsWith("Template")) cleanName = cleanName.dropLast(8)
        else if (cleanName.endsWith("Def")) cleanName = cleanName.dropLast(3)

        val defaultPacketName = if (cleanName.endsWith("Packet")) "${cleanName}Impl" else "${cleanName}PacketImpl"

        val customName = ann?.arg<String>("name") ?: ""
        val path       = ann?.arg<String>("path") ?: ""
        val packetName = if (customName.isNotEmpty()) customName else defaultPacketName
        val packetPkg  = pkg + path

        val ifaceClass = ClassName.get(pkg, interfaceName)
        val generatedClass = ClassName.get(packetPkg, packetName)

        val isPooled = decl.annotations.any { it.shortName.asString() == "Pooled" }

        val noArgCtor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).build()
        val encodeCtor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC)
        val ofFactory  = MethodSpec.methodBuilder("of")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(generatedClass)

        val acquireBuilder = MethodSpec.methodBuilder("acquire")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(generatedClass)

        val resetBuilder = MethodSpec.methodBuilder("reset")
            .addModifiers(Modifier.PUBLIC)

        val writeMethod = MethodSpec.methodBuilder("write")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .addParameter(ParameterSpec.builder(TypeName.get(BufferWriter::class.java), "buffer", Modifier.FINAL).build())
            .returns(TypeName.VOID)

        val readMethod = MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .addParameter(ParameterSpec.builder(TypeName.get(BufferReader::class.java), "buffer", Modifier.FINAL).build())
            .returns(TypeName.VOID)

        val classBuilder = TypeSpec.classBuilder(packetName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .superclass(ClassName.get(Packet::class.java))
            .addSuperinterface(ifaceClass)

        if (isPooled) {
            val poolType = ParameterizedTypeName.get(ClassName.get(ObjectPool::class.java), generatedClass)
            val poolField = FieldSpec.builder(poolType, "POOL", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("\$T.threadLocal(\$T::new).reset(\$T::reset).build()", ClassName.get(ObjectPool::class.java), generatedClass, generatedClass)
                .build()
            classBuilder.addField(poolField)
            acquireBuilder.addStatement("\$T instance = POOL.acquire()", generatedClass)
        }

        val fieldNames = mutableListOf<String>()

        for (func in decl.getDeclaredFunctions()) {
            // Strict Filter: abstract only, 0 parameters, non-void, non-ignored
            if (!func.isAbstract || func.parameters.isNotEmpty()) continue
            if (func.annotation(IGNORE_ANN) != null) continue
            val returnType = func.returnType?.resolve() ?: continue
            if (returnType.declaration.qualifiedName?.asString() == "kotlin.Unit") continue

            val originalName = func.simpleName.asString()
            val fieldAnn     = func.annotation(BUILD_PACKET)
            val fieldCustom  = fieldAnn?.arg<String>("name") ?: ""
            val addMethod    = fieldAnn?.arg<Boolean>("addMethod") ?: false
            val fieldName    = if (fieldCustom.isNotEmpty()) fieldCustom else originalName
            val replace      = fieldCustom.isNotEmpty() && addMethod

            val typeName = returnType.toJavaPoet()
            fieldNames.add(fieldName)

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

            encodeCtor.addParameter(typeName, fieldName, Modifier.FINAL)
            encodeCtor.addStatement("this.\$N = \$N", fieldName, fieldName)

            ofFactory.addParameter(typeName, fieldName, Modifier.FINAL)

            if (isPooled) {
                acquireBuilder.addParameter(typeName, fieldName, Modifier.FINAL)
                acquireBuilder.addStatement("instance.\$N = \$N", fieldName, fieldName)
                addResetStatement(resetBuilder, fieldName, typeName)
            }

            val fbAnn = func.annotation(FIELD_BUFFER)
            if (fbAnn != null) {
                val encoderType = fbAnn.kstypeArg("encoder")?.toJavaPoet()
                val decoderType = fbAnn.kstypeArg("decoder")?.toJavaPoet()
                if (encoderType != null && decoderType != null) {
                    val bu = ClassName.bestGuess(BUFFER_UTILS)
                    writeMethod.addStatement("buffer.writeObject(\$N, \$T.encoder(\$T.class))", fieldName, bu, encoderType)
                    readMethod.addStatement("this.\$N = buffer.readObject(\$T.decoder(\$T.class))", fieldName, bu, decoderType)
                }
                continue
            }

            generateBuffer(fieldName, returnType, typeName, writeMethod, readMethod)
        }

        ofFactory.addStatement("return new \$T(${fieldNames.joinToString(", ")})", generatedClass)

        classBuilder
            .addMethod(noArgCtor)
            .addMethod(encodeCtor.build())
            .addMethod(ofFactory.build())
            .addMethod(writeMethod.build())
            .addMethod(readMethod.build())

        if (isPooled) {
            acquireBuilder.addStatement("return instance")
            classBuilder.addMethod(acquireBuilder.build())
            classBuilder.addMethod(resetBuilder.build())

            val releaseMethod = MethodSpec.methodBuilder("release")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("POOL.release(this)")
                .build()
            classBuilder.addMethod(releaseMethod)
        }

        ann.classListArg("annotations").forEach { classBuilder.addAnnotation(it) }

        val file = codeGenerator.createNewFile(
            Dependencies(false, decl.containingFile!!), packetPkg, packetName, "java"
        )
        file.bufferedWriter().use { JavaFile.builder(packetPkg, classBuilder.build()).build().writeTo(it) }
    }

    private fun addResetStatement(reset: MethodSpec.Builder, fieldName: String, type: TypeName) {
        if (type.isPrimitive) {
            if (type == TypeName.BOOLEAN) reset.addStatement("this.\$N = false", fieldName)
            else reset.addStatement("this.\$N = 0", fieldName)
        } else {
            reset.addStatement("this.\$N = null", fieldName)
        }
    }

    private fun generateBuffer(
        fieldName: String, ksType: KSType, typeName: TypeName,
        write: MethodSpec.Builder, read: MethodSpec.Builder,
    ) {
        val qName = ksType.declaration.qualifiedName?.asString() ?: run {
            logger.error("Cannot resolve type for field $fieldName"); return
        }
        when (qName) {
            "kotlin.Boolean"      -> { write.addStatement("buffer.writeBoolean(\$N)", fieldName);    read.addStatement("this.\$N = buffer.readBoolean()", fieldName) }
            "kotlin.Float"        -> { write.addStatement("buffer.writeFloat(\$N)", fieldName);      read.addStatement("this.\$N = buffer.readFloat()", fieldName) }
            "kotlin.Short"        -> { write.addStatement("buffer.writeShort(\$N)", fieldName);      read.addStatement("this.\$N = buffer.readShort()", fieldName) }
            "kotlin.Byte"         -> { write.addStatement("buffer.writeByte(\$N)", fieldName);       read.addStatement("this.\$N = buffer.readByte()", fieldName) }
            "kotlin.Double"       -> { write.addStatement("buffer.writeDouble(\$N)", fieldName);     read.addStatement("this.\$N = buffer.readDouble()", fieldName) }
            "kotlin.Char"         -> { write.addStatement("buffer.writeChar(\$N)", fieldName);       read.addStatement("this.\$N = buffer.readChar()", fieldName) }
            "kotlin.Int"          -> { write.addStatement("buffer.writeVarInt(\$N)", fieldName);     read.addStatement("this.\$N = buffer.readVarInt()", fieldName) }
            "kotlin.Long"         -> { write.addStatement("buffer.writeVarLong(\$N)", fieldName);    read.addStatement("this.\$N = buffer.readVarLong()", fieldName) }
            "kotlin.String"       -> { write.addStatement("buffer.writeString(\$N)", fieldName);     read.addStatement("this.\$N = buffer.readString()", fieldName) }
            "java.util.UUID"      -> { write.addStatement("buffer.writeUuid(\$N)", fieldName);       read.addStatement("this.\$N = buffer.readUuid()", fieldName) }
            "kotlin.BooleanArray" -> { write.addStatement("buffer.writeBooleanArray(\$N)", fieldName); read.addStatement("this.\$N = buffer.readBooleanArray()", fieldName) }
            "kotlin.FloatArray"   -> { write.addStatement("buffer.writeFloatArray(\$N)", fieldName);   read.addStatement("this.\$N = buffer.readFloatArray()", fieldName) }
            "kotlin.ShortArray"   -> { write.addStatement("buffer.writeShortArray(\$N)", fieldName);   read.addStatement("this.\$N = buffer.readShortArray()", fieldName) }
            "kotlin.ByteArray"    -> { write.addStatement("buffer.writeByteArray(\$N)", fieldName);    read.addStatement("this.\$N = buffer.readByteArray()", fieldName) }
            "kotlin.DoubleArray"  -> { write.addStatement("buffer.writeDoubleArray(\$N)", fieldName);  read.addStatement("this.\$N = buffer.readDoubleArray()", fieldName) }
            "kotlin.CharArray"    -> { write.addStatement("buffer.writeCharArray(\$N)", fieldName);    read.addStatement("this.\$N = buffer.readCharArray()", fieldName) }
            "kotlin.IntArray"     -> { write.addStatement("buffer.writeVarIntArray(\$N)", fieldName);  read.addStatement("this.\$N = buffer.readVarIntArray()", fieldName) }
            "kotlin.LongArray"    -> { write.addStatement("buffer.writeVarLongArray(\$N)", fieldName); read.addStatement("this.\$N = buffer.readVarLongArray()", fieldName) }
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
                write.addStatement("buffer.writeEnum(\$N)", fieldName)
                read.addStatement("this.\$N = buffer.readEnum(\$T.class)", fieldName, typeName)
            }
            ksType.implementsIface(ABS_ENCODER) && ksType.implementsIface(ABS_DECODER) -> {
                write.addStatement("buffer.writeObject(\$N)", fieldName)
                read.addStatement("this.\$N = buffer.readObject(\$T::new)", fieldName, typeName)
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
            "kotlin.String"  -> { write.addStatement("buffer.writeArray(\$T::writeString, \$N)", bw, fieldName);  read.addStatement("this.\$N = buffer.readArray(\$T::readString, \$T[]::new)", fieldName, br, ClassName.get(String::class.java)) }
            "java.util.UUID" -> { write.addStatement("buffer.writeArray(\$T::writeUuid, \$N)", bw, fieldName);    read.addStatement("this.\$N = buffer.readArray(\$T::readUuid, \$T[]::new)", fieldName, br, ClassName.get("java.util", "UUID")) }
            else -> {
                val compDecl = componentKsType.declaration as? KSClassDeclaration
                if (compDecl?.classKind == ClassKind.ENUM_CLASS) {
                    write.addStatement("buffer.writeArray(\$T::writeEnum, \$N)", bw, fieldName)
                    read.addStatement("this.\$N = buffer.readArray(buffer -> buffer.readEnum(\$T.class), \$T[]::new)", fieldName, componentTypeName, componentTypeName)
                } else {
                    when (componentQName) {
                        "kotlin.Int"     -> { write.addStatement("buffer.writeArray(\$T::writeVarInt, \$N)", bw, fieldName);   read.addStatement("this.\$N = buffer.readArray(\$T::readVarInt, \$T[]::new)", fieldName, br, ClassName.get(java.lang.Integer::class.java)) }
                        "kotlin.Boolean" -> { write.addStatement("buffer.writeArray(\$T::writeBoolean, \$N)", bw, fieldName);  read.addStatement("this.\$N = buffer.readArray(\$T::readBoolean, \$T[]::new)", fieldName, br, ClassName.get(java.lang.Boolean::class.java)) }
                        "kotlin.Long"    -> { write.addStatement("buffer.writeArray(\$T::writeVarLong, \$N)", bw, fieldName);  read.addStatement("this.\$N = buffer.readArray(\$T::readVarLong, \$T[]::new)", fieldName, br, ClassName.get(java.lang.Long::class.java)) }
                        "kotlin.Short"   -> { write.addStatement("buffer.writeArray(\$T::writeShort, \$N)", bw, fieldName);   read.addStatement("this.\$N = buffer.readArray(\$T::readShort, \$T[]::new)", fieldName, br, ClassName.get(java.lang.Short::class.java)) }
                        "kotlin.Float"   -> { write.addStatement("buffer.writeArray(\$T::writeFloat, \$N)", bw, fieldName);   read.addStatement("this.\$N = buffer.readArray(\$T::readFloat, \$T[]::new)", fieldName, br, ClassName.get(java.lang.Float::class.java)) }
                        "kotlin.Double"  -> { write.addStatement("buffer.writeArray(\$T::writeDouble, \$N)", bw, fieldName);  read.addStatement("this.\$N = buffer.readArray(\$T::readDouble, \$T[]::new)", fieldName, br, ClassName.get(java.lang.Double::class.java)) }
                        "kotlin.Byte"    -> { write.addStatement("buffer.writeArray(\$T::writeByte, \$N)", bw, fieldName);    read.addStatement("this.\$N = buffer.readArray(\$T::readByte, \$T[]::new)", fieldName, br, ClassName.get(java.lang.Byte::class.java)) }
                        else -> logger.error("Unsupported array component $componentQName in field $fieldName")
                    }
                }
            }
        }
    }
}
