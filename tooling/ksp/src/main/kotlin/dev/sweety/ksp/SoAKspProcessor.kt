package dev.sweety.ksp

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier as KspModifier
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.validate
import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.AbstractList
import java.util.RandomAccess
import javax.lang.model.element.Modifier

private const val SOA_ANNOTATION = "dev.sweety.math.soa.SoA"

class SoAKspProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        resolver.getSymbolsWithAnnotation(SOA_ANNOTATION).forEach { sym ->
            if (sym is KSClassDeclaration && (sym.classKind == ClassKind.CLASS || sym.classKind == ClassKind.INTERFACE)) {
                if (sym.validate()) {
                    try {
                        generateSoAList(sym)
                    } catch (e: Exception) {
                        logger.error("Failed to generate SoA list for ${sym.qualifiedName?.asString()}: ${e.message}", sym)
                    }
                } else {
                    deferred += sym
                }
            }
        }
        return deferred
    }

    private data class PropertyInfo(
        val name: String,
        val fieldType: TypeName,
        val boxedType: TypeName,
        val listType: TypeName,
        val rawArrayType: TypeName,
        val isPrimitive: Boolean,
        val getterOnList: String,
        val setterOnList: String,
        val removeOnList: String,
        val byteSizeExpr: String?,
        val accessorExpr: String,
    )

    private enum class SupportedPrimitive(
        val typeName: TypeName,
        val listClassName: ClassName,
        val arrayTypeName: ArrayTypeName,
        val getterMethod: String,
        val setterMethod: String,
        val removeMethod: String,
        val byteSizeExpr: String,
    ) {
        INT(
            TypeName.INT,
            ClassName.get("it.unimi.dsi.fastutil.ints", "IntArrayList"),
            ArrayTypeName.of(TypeName.INT),
            "getInt",
            "set",
            "removeInt",
            "Integer.BYTES"
        ),
        LONG(
            TypeName.LONG,
            ClassName.get("it.unimi.dsi.fastutil.longs", "LongArrayList"),
            ArrayTypeName.of(TypeName.LONG),
            "getLong",
            "set",
            "removeLong",
            "Long.BYTES"
        ),
        DOUBLE(
            TypeName.DOUBLE,
            ClassName.get("it.unimi.dsi.fastutil.doubles", "DoubleArrayList"),
            ArrayTypeName.of(TypeName.DOUBLE),
            "getDouble",
            "set",
            "removeDouble",
            "Double.BYTES"
        ),
        FLOAT(
            TypeName.FLOAT,
            ClassName.get("it.unimi.dsi.fastutil.floats", "FloatArrayList"),
            ArrayTypeName.of(TypeName.FLOAT),
            "getFloat",
            "set",
            "removeFloat",
            "Float.BYTES"
        ),
        BOOLEAN(
            TypeName.BOOLEAN,
            ClassName.get("it.unimi.dsi.fastutil.booleans", "BooleanArrayList"),
            ArrayTypeName.of(TypeName.BOOLEAN),
            "getBoolean",
            "set",
            "removeBoolean",
            "1L"
        ),
        SHORT(
            TypeName.SHORT,
            ClassName.get("it.unimi.dsi.fastutil.shorts", "ShortArrayList"),
            ArrayTypeName.of(TypeName.SHORT),
            "getShort",
            "set",
            "removeShort",
            "Short.BYTES"
        ),
        BYTE(
            TypeName.BYTE,
            ClassName.get("it.unimi.dsi.fastutil.bytes", "ByteArrayList"),
            ArrayTypeName.of(TypeName.BYTE),
            "getByte",
            "set",
            "removeByte",
            "Byte.BYTES"
        ),
        CHAR(
            TypeName.CHAR,
            ClassName.get("it.unimi.dsi.fastutil.chars", "CharArrayList"),
            ArrayTypeName.of(TypeName.CHAR),
            "getChar",
            "set",
            "removeChar",
            "Character.BYTES"
        );

        companion object {
            fun from(qName: String?, isNullable: Boolean): SupportedPrimitive? {
                if (isNullable || qName == null) return null
                return when (qName) {
                    "int", "kotlin.Int" -> INT
                    "long", "kotlin.Long" -> LONG
                    "double", "kotlin.Double" -> DOUBLE
                    "float", "kotlin.Float" -> FLOAT
                    "boolean", "kotlin.Boolean" -> BOOLEAN
                    "short", "kotlin.Short" -> SHORT
                    "byte", "kotlin.Byte" -> BYTE
                    "char", "kotlin.Char" -> CHAR
                    else -> null
                }
            }
        }
    }

    private fun generateSoAList(decl: KSClassDeclaration) {
        val ann = decl.annotation(SOA_ANNOTATION)
        val customListName = ann?.arguments?.firstOrNull {
            it.name?.asString() == "listName" || it.name == null || it.name?.asString() == "value"
        }?.value as? String
        val cleanListName = customListName?.trim().orEmpty()

        val entityName = decl.simpleName.asString()
        val pkg = decl.packageName.asString()
        val listClassName = if (cleanListName.isNotEmpty()) cleanListName else "${entityName}List"

        val recordClass = decl.toClassName()
        val properties = extractProperties(decl)

        val listSuperClass = ParameterizedTypeName.get(ClassName.get(AbstractList::class.java), recordClass)
        val classBuilder = TypeSpec.classBuilder(listClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .superclass(listSuperClass)
            .addSuperinterface(ClassName.get(RandomAccess::class.java))

        // Holds private final fields for each column list
        for (prop in properties) {
            val fieldSpec = FieldSpec.builder(prop.listType, prop.name, Modifier.PRIVATE, Modifier.FINAL)
                .build()
            classBuilder.addField(fieldSpec)
        }

        // Constructors: default (capacity 16)
        val defaultCtor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addStatement("this(16)")
            .build()
        classBuilder.addMethod(defaultCtor)

        // Constructor: (int capacity)
        val capCtor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(TypeName.INT, "capacity")

        for (prop in properties) {
            if (prop.isPrimitive) {
                capCtor.addStatement("this.\$L = new \$T(capacity)", prop.name, prop.listType)
            } else {
                capCtor.addStatement(
                    "this.\$L = new \$T<>(capacity)",
                    prop.name,
                    ClassName.get("it.unimi.dsi.fastutil.objects", "ObjectArrayList")
                )
            }
        }
        classBuilder.addMethod(capCtor.build())

        // Column getters: e.g. public IntArrayList idList(), public DoubleArrayList xList()
        for (prop in properties) {
            val listGetterName = "${prop.name}List"
            val listGetter = MethodSpec.methodBuilder(listGetterName)
                .addModifiers(Modifier.PUBLIC)
                .returns(prop.listType)
                .addStatement("return this.\$L", prop.name)
                .build()
            classBuilder.addMethod(listGetter)
        }

        // Direct raw array exports for SIMD / Vector API / GPU: public double[] rawX() { return x.elements(); }
        for (prop in properties) {
            val capName = prop.name.replaceFirstChar { it.uppercaseChar() }
            val rawMethodName = "raw$capName"
            val rawMb = MethodSpec.methodBuilder(rawMethodName)
                .addModifiers(Modifier.PUBLIC)
                .returns(prop.rawArrayType)

            if (prop.isPrimitive) {
                rawMb.addStatement("return this.\$L.elements()", prop.name)
            } else {
                rawMb.addAnnotation(
                    AnnotationSpec.builder(SuppressWarnings::class.java).addMember("value", "\$S", "unchecked").build()
                )
                rawMb.addStatement("return (\$T) this.\$L.elements()", prop.rawArrayType, prop.name)
            }
            classBuilder.addMethod(rawMb.build())
        }

        // Direct primitive getters and setters: public double getX(int index), public void setX(int index, double value)
        for (prop in properties) {
            val capName = prop.name.replaceFirstChar { it.uppercaseChar() }
            val getterName = "get$capName"
            val setterName = "set$capName"

            // Getter
            val getterMb = MethodSpec.methodBuilder(getterName)
                .addModifiers(Modifier.PUBLIC)
                .returns(prop.fieldType)
                .addParameter(TypeName.INT, "index")
                .addStatement("return this.\$L.\$L(index)", prop.name, prop.getterOnList)
            classBuilder.addMethod(getterMb.build())

            // Optional isX alias for boolean if not already starting with is
            if (prop.fieldType == TypeName.BOOLEAN) {
                val isName = if (prop.name.startsWith("is")) prop.name else "is$capName"
                if (isName != getterName) {
                    val isMb = MethodSpec.methodBuilder(isName)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(TypeName.BOOLEAN)
                        .addParameter(TypeName.INT, "index")
                        .addStatement("return this.\$L.\$L(index)", prop.name, prop.getterOnList)
                    classBuilder.addMethod(isMb.build())
                }
            }

            // Setter
            val setterMb = MethodSpec.methodBuilder(setterName)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.VOID)
                .addParameter(TypeName.INT, "index")
                .addParameter(prop.fieldType, "value")
                .addStatement("this.\$L.\$L(index, value)", prop.name, prop.setterOnList)
            classBuilder.addMethod(setterMb.build())
        }

        // Primitive bulk add: public void add(int id, double x, double y, ...)
        val bulkAddMb = MethodSpec.methodBuilder("add")
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.VOID)

        for (prop in properties) {
            bulkAddMb.addParameter(prop.fieldType, prop.name)
        }
        for (prop in properties) {
            bulkAddMb.addStatement("this.\$L.add(\$L)", prop.name, prop.name)
        }
        classBuilder.addMethod(bulkAddMb.build())

        // Standard List methods
        // public int size()
        val sizeMb = MethodSpec.methodBuilder("size")
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.INT)

        if (properties.isEmpty()) {
            sizeMb.addStatement("return 0")
        } else {
            sizeMb.addStatement("return this.\$L.size()", properties[0].name)
        }
        classBuilder.addMethod(sizeMb.build())

        // public boolean isEmpty()
        val isEmptyMb = MethodSpec.methodBuilder("isEmpty")
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.BOOLEAN)

        if (properties.isEmpty()) {
            isEmptyMb.addStatement("return true")
        } else {
            isEmptyMb.addStatement("return this.\$L.isEmpty()", properties[0].name)
        }
        classBuilder.addMethod(isEmptyMb.build())

        // public ${RecordName} get(int index) returning new ${RecordName}(id.getInt(index), x.getDouble(index), ...)
        val getMb = MethodSpec.methodBuilder("get")
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .returns(recordClass)
            .addParameter(TypeName.INT, "index")

        val argsBlock = CodeBlock.builder()
        for (i in properties.indices) {
            if (i > 0) argsBlock.add(", ")
            val prop = properties[i]
            argsBlock.add("this.\$L.\$L(index)", prop.name, prop.getterOnList)
        }
        getMb.addStatement("return new \$T(\$L)", recordClass, argsBlock.build())
        classBuilder.addMethod(getMb.build())

        // public boolean add(${RecordName} element) unpacking element into parallel lists
        val addMb = MethodSpec.methodBuilder("add")
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.BOOLEAN)
            .addParameter(recordClass, "element")

        addMb.beginControlFlow("if (element == null)")
            .addStatement("throw new \$T(\$S)", NullPointerException::class.java, "element cannot be null")
            .endControlFlow()

        for (prop in properties) {
            addMb.addStatement("this.\$L.add(element.\$L)", prop.name, prop.accessorExpr)
        }
        addMb.addStatement("return true")
        classBuilder.addMethod(addMb.build())

        // public void add(int index, ${RecordName} element)
        val addIndexMb = MethodSpec.methodBuilder("add")
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.VOID)
            .addParameter(TypeName.INT, "index")
            .addParameter(recordClass, "element")

        addIndexMb.beginControlFlow("if (element == null)")
            .addStatement("throw new \$T(\$S)", NullPointerException::class.java, "element cannot be null")
            .endControlFlow()

        for (prop in properties) {
            addIndexMb.addStatement("this.\$L.add(index, element.\$L)", prop.name, prop.accessorExpr)
        }
        classBuilder.addMethod(addIndexMb.build())

        // public ${RecordName} set(int index, ${RecordName} element)
        val setMb = MethodSpec.methodBuilder("set")
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .returns(recordClass)
            .addParameter(TypeName.INT, "index")
            .addParameter(recordClass, "element")

        setMb.beginControlFlow("if (element == null)")
            .addStatement("throw new \$T(\$S)", NullPointerException::class.java, "element cannot be null")
            .endControlFlow()

        setMb.addStatement("\$T old = get(index)", recordClass)
        for (prop in properties) {
            setMb.addStatement("this.\$L.\$L(index, element.\$L)", prop.name, prop.setterOnList, prop.accessorExpr)
        }
        setMb.addStatement("return old")
        classBuilder.addMethod(setMb.build())

        // public ${RecordName} remove(int index)
        val removeMb = MethodSpec.methodBuilder("remove")
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .returns(recordClass)
            .addParameter(TypeName.INT, "index")

        removeMb.addStatement("\$T old = get(index)", recordClass)
        for (prop in properties) {
            removeMb.addStatement("this.\$L.\$L(index)", prop.name, prop.removeOnList)
        }
        removeMb.addStatement("return old")
        classBuilder.addMethod(removeMb.build())

        // public void clear()
        val clearMb = MethodSpec.methodBuilder("clear")
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.VOID)

        for (prop in properties) {
            clearMb.addStatement("this.\$L.clear()", prop.name)
        }
        classBuilder.addMethod(clearMb.build())

        // public void ensureCapacity(int minCapacity)
        val ensureCapMb = MethodSpec.methodBuilder("ensureCapacity")
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.VOID)
            .addParameter(TypeName.INT, "minCapacity")

        for (prop in properties) {
            ensureCapMb.addStatement("this.\$L.ensureCapacity(minCapacity)", prop.name)
        }
        classBuilder.addMethod(ensureCapMb.build())

        // public void trim()
        val trimMb = MethodSpec.methodBuilder("trim")
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.VOID)

        for (prop in properties) {
            trimMb.addStatement("this.\$L.trim()", prop.name)
        }
        classBuilder.addMethod(trimMb.build())

        // public long primitiveByteSize() returning exact bytes of underlying primitive arrays
        val pbsMb = MethodSpec.methodBuilder("primitiveByteSize")
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.LONG)

        val primProps = properties.filter { it.isPrimitive }
        if (primProps.isEmpty()) {
            pbsMb.addStatement("return 0L")
        } else {
            pbsMb.addStatement("long bytes = 0L")
            for (prop in primProps) {
                pbsMb.addStatement(
                    "bytes += (long) this.\$L.elements().length * \$L",
                    prop.name,
                    prop.byteSizeExpr
                )
            }
            pbsMb.addStatement("return bytes")
        }
        classBuilder.addMethod(pbsMb.build())

        // Write Java File
        val javaFile = JavaFile.builder(pkg, classBuilder.build())
            .skipJavaLangImports(true)
            .build()

        val deps = decl.containingFile?.let { Dependencies(false, it) } ?: Dependencies(false)
        val file = codeGenerator.createNewFile(
            deps,
            pkg,
            listClassName,
            "java"
        )
        OutputStreamWriter(file, StandardCharsets.UTF_8).use { javaFile.writeTo(it) }
    }

    private fun extractProperties(decl: KSClassDeclaration): List<PropertyInfo> {
        val declaredProps = decl.getDeclaredProperties()
            .filter { prop ->
                !prop.modifiers.contains(KspModifier.JAVA_STATIC) &&
                !prop.modifiers.contains(KspModifier.CONST)
            }
            .toList()

        val primaryCtor = decl.primaryConstructor
        val ctorParams = primaryCtor?.parameters ?: emptyList()

        val rawProps: List<Pair<String, KSType>> = if (ctorParams.isNotEmpty()) {
            ctorParams.mapNotNull { param ->
                val name = param.name?.asString() ?: return@mapNotNull null
                val propDecl = declaredProps.find { it.simpleName.asString() == name }
                val kstype = propDecl?.type?.resolve() ?: param.type.resolve()
                name to kstype
            }
        } else {
            declaredProps.map { prop ->
                prop.simpleName.asString() to prop.type.resolve()
            }
        }

        return rawProps.map { (name, kstype) ->
            val qName = kstype.declaration.qualifiedName?.asString()
            val isNullable = kstype.isMarkedNullable
            val prim = SupportedPrimitive.from(qName, isNullable)
            val fieldType = kstype.toJavaPoetType(box = false)
            val boxedType = kstype.toJavaPoetType(box = true)
            val accessor = resolveAccessor(name, fieldType, decl)

            if (prim != null) {
                PropertyInfo(
                    name = name,
                    fieldType = prim.typeName,
                    boxedType = boxedType,
                    listType = prim.listClassName,
                    rawArrayType = prim.arrayTypeName,
                    isPrimitive = true,
                    getterOnList = prim.getterMethod,
                    setterOnList = prim.setterMethod,
                    removeOnList = prim.removeMethod,
                    byteSizeExpr = prim.byteSizeExpr,
                    accessorExpr = accessor
                )
            } else {
                val listType = ParameterizedTypeName.get(
                    ClassName.get("it.unimi.dsi.fastutil.objects", "ObjectArrayList"),
                    boxedType
                )
                PropertyInfo(
                    name = name,
                    fieldType = fieldType,
                    boxedType = boxedType,
                    listType = listType,
                    rawArrayType = ArrayTypeName.of(boxedType),
                    isPrimitive = false,
                    getterOnList = "get",
                    setterOnList = "set",
                    removeOnList = "remove",
                    byteSizeExpr = null,
                    accessorExpr = accessor
                )
            }
        }
    }

    private fun resolveAccessor(
        propName: String,
        fieldType: TypeName,
        decl: KSClassDeclaration
    ): String {
        val isRecord = decl.superTypes.any {
            it.resolve().declaration.qualifiedName?.asString() == "java.lang.Record"
        }
        if (isRecord) {
            return "$propName()"
        }

        val declaredProps = decl.getDeclaredProperties().toList()
        val propDecl = declaredProps.find { it.simpleName.asString() == propName }
        val hasJvmField = propDecl?.annotations?.any {
            it.shortName.asString() == "JvmField" ||
            it.annotationType.resolve().declaration.qualifiedName?.asString() == "kotlin.jvm.JvmField"
        } ?: false
        if (hasJvmField) {
            return propName
        }

        val allFunctions = decl.getAllFunctions().toList()

        if (allFunctions.any { it.simpleName.asString() == propName && it.parameters.isEmpty() }) {
            return "$propName()"
        }

        val cap = propName.replaceFirstChar { it.uppercaseChar() }
        val getGetter = "get$cap"
        if (allFunctions.any { it.simpleName.asString() == getGetter && it.parameters.isEmpty() }) {
            return "$getGetter()"
        }

        if (fieldType == TypeName.BOOLEAN) {
            val isGetter = if (propName.startsWith("is")) propName else "is$cap"
            if (allFunctions.any { it.simpleName.asString() == isGetter && it.parameters.isEmpty() }) {
                return "$isGetter()"
            }
        }

        if (decl.origin == Origin.KOTLIN || decl.origin == Origin.KOTLIN_LIB) {
            if (fieldType == TypeName.BOOLEAN) {
                val isGetter = if (propName.startsWith("is")) propName else "is$cap"
                return "$isGetter()"
            }
            return "$getGetter()"
        }

        val isField = declaredProps.any { it.simpleName.asString() == propName }
        return if (isField) propName else "$propName()"
    }

    private fun KSType.toJavaPoetType(box: Boolean = false): TypeName {
        val qName = declaration.qualifiedName?.asString()
        if (!box && !isMarkedNullable) {
            when (qName) {
                "int", "kotlin.Int" -> return TypeName.INT
                "long", "kotlin.Long" -> return TypeName.LONG
                "double", "kotlin.Double" -> return TypeName.DOUBLE
                "float", "kotlin.Float" -> return TypeName.FLOAT
                "boolean", "kotlin.Boolean" -> return TypeName.BOOLEAN
                "short", "kotlin.Short" -> return TypeName.SHORT
                "byte", "kotlin.Byte" -> return TypeName.BYTE
                "char", "kotlin.Char" -> return TypeName.CHAR
                "void", "kotlin.Unit" -> return TypeName.VOID
            }
        }
        return when (qName) {
            "int", "kotlin.Int", "java.lang.Integer" -> ClassName.get("java.lang", "Integer")
            "long", "kotlin.Long", "java.lang.Long" -> ClassName.get("java.lang", "Long")
            "double", "kotlin.Double", "java.lang.Double" -> ClassName.get("java.lang", "Double")
            "float", "kotlin.Float", "java.lang.Float" -> ClassName.get("java.lang", "Float")
            "boolean", "kotlin.Boolean", "java.lang.Boolean" -> ClassName.get("java.lang", "Boolean")
            "short", "kotlin.Short", "java.lang.Short" -> ClassName.get("java.lang", "Short")
            "byte", "kotlin.Byte", "java.lang.Byte" -> ClassName.get("java.lang", "Byte")
            "char", "kotlin.Char", "java.lang.Character" -> ClassName.get("java.lang", "Character")
            "kotlin.String", "java.lang.String" -> ClassName.get("java.lang", "String")
            "kotlin.IntArray" -> ArrayTypeName.of(TypeName.INT)
            "kotlin.LongArray" -> ArrayTypeName.of(TypeName.LONG)
            "kotlin.DoubleArray" -> ArrayTypeName.of(TypeName.DOUBLE)
            "kotlin.FloatArray" -> ArrayTypeName.of(TypeName.FLOAT)
            "kotlin.BooleanArray" -> ArrayTypeName.of(TypeName.BOOLEAN)
            "kotlin.ShortArray" -> ArrayTypeName.of(TypeName.SHORT)
            "kotlin.ByteArray" -> ArrayTypeName.of(TypeName.BYTE)
            "kotlin.CharArray" -> ArrayTypeName.of(TypeName.CHAR)
            null -> ClassName.OBJECT
            else -> {
                val pkg = declaration.packageName.asString()
                val simple = declaration.simpleName.asString()
                val raw = ClassName.get(pkg, simple)
                if (arguments.isNotEmpty()) {
                    val typeArgs = arguments.mapNotNull { it.type?.resolve()?.toJavaPoetType(box = true) }
                    if (typeArgs.size == arguments.size) {
                        ParameterizedTypeName.get(raw, *typeArgs.toTypedArray())
                    } else raw
                } else {
                    raw
                }
            }
        }
    }

    private fun KSClassDeclaration.toClassName(): ClassName {
        val pkg = packageName.asString()
        val names = mutableListOf<String>()
        var current: KSDeclaration? = this
        while (current is KSClassDeclaration) {
            names.add(0, current.simpleName.asString())
            current = current.parentDeclaration
        }
        return ClassName.get(pkg, names.first(), *names.drop(1).toTypedArray())
    }
}
