package dev.sweety.ksp

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.validate
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import dev.sweety.sql4j.api.obj.Column
import dev.sweety.sql4j.api.obj.PrimitiveKind
import dev.sweety.sql4j.api.obj.Table
import dev.sweety.sql4j.api.obj.TableAccessor
import dev.sweety.sql4j.api.obj.table.TableRegistry
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.lang.model.element.Modifier

private const val TABLE_INFO = "dev.sweety.sql4j.api.obj.annotation.Table.Info"
private const val COLUMN_INFO = "dev.sweety.sql4j.api.obj.annotation.Column.Info"
private const val PRIMARY_KEY = "dev.sweety.sql4j.api.obj.annotation.PrimaryKey"
private const val AUTO_INCREMENT = "dev.sweety.sql4j.api.obj.annotation.AutoIncrement"
private const val SOFT_DELETE = "dev.sweety.sql4j.api.obj.annotation.SoftDelete"
private const val INDEX = "dev.sweety.sql4j.api.obj.annotation.Index"
private const val INDEXES = "dev.sweety.sql4j.api.obj.annotation.Indexes"
private const val MANY_TO_ONE = "dev.sweety.sql4j.api.obj.annotation.relation.ManyToOne"
private const val ONE_TO_MANY = "dev.sweety.sql4j.api.obj.annotation.relation.OneToMany"
private const val MANY_TO_MANY = "dev.sweety.sql4j.api.obj.annotation.relation.ManyToMany"

class TableKspProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    override fun process(resolver: Resolver): kotlin.collections.List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        resolver.getSymbolsWithAnnotation(TABLE_INFO).forEach { sym ->
            if (sym is KSClassDeclaration && (sym.classKind == ClassKind.CLASS || sym.classKind == ClassKind.INTERFACE)) {
                if (sym.validate()) generateTable(sym)
                else deferred += sym
            }
        }
        return deferred
    }

    private data class PropertyData(
        val propName: String,
        val colName: String,
        val fieldType: TypeName,
        val boxedType: TypeName,
        val columnType: TypeName,
        val isPrimaryKey: Boolean,
        val isAutoInc: Boolean,
        val isNullable: Boolean,
        val isSoftDelete: Boolean,
        val indexName: String?,
        val isUniqueIndex: Boolean,
        val relationKind: RelationKind?,
        val relationTargetClass: TypeName?,
        val mappedBy: String?,
    )

    private enum class RelationKind {
        MANY_TO_ONE, ONE_TO_MANY, MANY_TO_MANY
    }

    private fun generateTable(decl: KSClassDeclaration) {
        val ann = decl.annotations.firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == TABLE_INFO }
        val rawTableName = ann?.arguments?.firstOrNull { it.name?.asString() == "value" }?.value as? String ?: ""
        val entityName = decl.simpleName.asString()
        val pkg = decl.packageName.asString()
        val tableName = if (rawTableName.isNotEmpty()) rawTableName else entityName.lowercase(Locale.ENGLISH)

        val entityClass = ClassName.get(pkg, entityName)
        val tableClassName = "${entityName}Table"

        val properties = decl.getDeclaredProperties().map { prop ->
            extractPropertyData(prop)
        }.toList()

        val tableSuperClass = ParameterizedTypeName.get(ClassName.get(Table::class.java), entityClass)
        val tableAccessorInterfaceType = ParameterizedTypeName.get(ClassName.get(TableAccessor::class.java), entityClass)

        val classBuilder = TypeSpec.classBuilder(tableClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .superclass(tableSuperClass)
            .addSuperinterface(tableAccessorInterfaceType)

        // Singleton INSTANCE
        val tableClassType = ClassName.get(pkg, tableClassName)
        classBuilder.addField(
            FieldSpec.builder(tableClassType, "INSTANCE", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("new \$T()", tableClassType)
                .build()
        )

        // Column Constants
        val columnClass = ClassName.get(Column::class.java)
        for (prop in properties) {
            val colConst = prop.propName.uppercase(Locale.ENGLISH)
            val paramType = if (prop.columnType != prop.boxedType) prop.columnType else prop.boxedType
            val colType = ParameterizedTypeName.get(columnClass, paramType)
            classBuilder.addField(
                FieldSpec.builder(colType, colConst, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL).build()
            )
        }

        // Static Initializer
        val staticBlock = CodeBlock.builder()
        for (prop in properties) {
            val colConst = prop.propName.uppercase(Locale.ENGLISH)
            val instType = if (prop.columnType != prop.boxedType) prop.columnType else prop.fieldType
            staticBlock.addStatement(
                "\$L = new \$T<>(INSTANCE, \$S, \$T.class, \$L, \$L, \$L)",
                colConst, columnClass, prop.colName, instType, prop.isPrimaryKey, prop.isAutoInc, prop.isNullable
            )
            if (prop.isSoftDelete) staticBlock.addStatement("\$L.setSoftDelete(true)", colConst)
            if (prop.columnType != prop.boxedType) staticBlock.addStatement("\$L.setRelation(true)", colConst)
            if (prop.indexName != null) {
                staticBlock.addStatement(
                    "INSTANCE.addIndex(new \$T.IndexDef(\$S, \$T.of(\$S), \$L))",
                    Table::class.java, prop.indexName, java.util.List::class.java, prop.colName, prop.isUniqueIndex
                )
            }
            staticBlock.addStatement("INSTANCE.addColumn(\$L)", colConst)
        }

        // Handle class-level indices
        extractClassIndices(decl, staticBlock)

        staticBlock.addStatement("INSTANCE.markInitialized()")
        staticBlock.addStatement("\$T.getDefault().register(INSTANCE)", TableRegistry::class.java)
        classBuilder.addStaticBlock(staticBlock.build())

        // Private Constructor
        classBuilder.addMethod(
            MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addStatement("super(\$T.class, \$S)", entityClass, tableName)
                .addStatement("setAccessor(this)")
                .build()
        )

        // Generate Primitive and Object Switch Accessors
        generatePrimitiveAccessors(classBuilder, entityClass, properties)

        // Write Java File
        val javaFile = JavaFile.builder(pkg, classBuilder.build())
            .skipJavaLangImports(true)
            .build()

        val file = codeGenerator.createNewFile(
            Dependencies(false, decl.containingFile!!),
            pkg,
            tableClassName
        )
        OutputStreamWriter(file, StandardCharsets.UTF_8).use { javaFile.writeTo(it) }
    }

    private fun extractPropertyData(prop: KSPropertyDeclaration): PropertyData {
        val propName = prop.simpleName.asString()
        val propType = prop.type.resolve()
        val fieldType = propType.toJavaPoet()
        val boxedType = propType.toJavaPoet(box = true)

        var colName = propName
        var isPrimaryKey = false
        var isAutoInc = false
        var isNullable = propType.isMarkedNullable
        var isSoftDelete = false
        var indexName: String? = null
        var isUniqueIndex = false
        var relationKind: RelationKind? = null
        var relationTargetClass: TypeName? = null
        var mappedBy: String? = null
        var columnType = boxedType

        for (ann in prop.annotations) {
            val qName = ann.annotationType.resolve().declaration.qualifiedName?.asString()
            when (qName) {
                COLUMN_INFO -> {
                    val customName = ann.arguments.firstOrNull { it.name?.asString() == "value" }?.value as? String ?: ""
                    if (customName.isNotEmpty()) colName = customName
                }
                PRIMARY_KEY -> isPrimaryKey = true
                AUTO_INCREMENT -> isAutoInc = true
                SOFT_DELETE -> isSoftDelete = true
                INDEX -> {
                    val idxVal = ann.arguments.firstOrNull { it.name?.asString() == "value" }?.value as? String ?: ""
                    indexName = if (idxVal.isNotEmpty()) idxVal else "idx_${propName}"
                    isUniqueIndex = ann.arguments.firstOrNull { it.name?.asString() == "unique" }?.value as? Boolean ?: false
                }
                MANY_TO_ONE -> {
                    relationKind = RelationKind.MANY_TO_ONE
                    relationTargetClass = boxedType
                    columnType = ClassName.get(Integer::class.java)
                    if (!colName.endsWith("_id")) colName = "${colName}_id"
                }
                ONE_TO_MANY -> {
                    relationKind = RelationKind.ONE_TO_MANY
                    mappedBy = ann.arguments.firstOrNull { it.name?.asString() == "mappedBy" }?.value as? String ?: ""
                }
                MANY_TO_MANY -> {
                    relationKind = RelationKind.MANY_TO_MANY
                }
            }
        }

        return PropertyData(
            propName = propName,
            colName = colName,
            fieldType = fieldType,
            boxedType = boxedType,
            columnType = columnType,
            isPrimaryKey = isPrimaryKey,
            isAutoInc = isAutoInc,
            isNullable = isNullable,
            isSoftDelete = isSoftDelete,
            indexName = indexName,
            isUniqueIndex = isUniqueIndex,
            relationKind = relationKind,
            relationTargetClass = relationTargetClass,
            mappedBy = mappedBy,
        )
    }

    private fun extractClassIndices(decl: KSClassDeclaration, staticBlock: CodeBlock.Builder) {
        val indexAnnotations = mutableListOf<KSAnnotation>()
        for (ann in decl.annotations) {
            val qName = ann.annotationType.resolve().declaration.qualifiedName?.asString()
            if (qName == INDEX) indexAnnotations += ann
            else if (qName == INDEXES) {
                val nested = ann.arguments.firstOrNull()?.value as? kotlin.collections.List<*>
                nested?.filterIsInstance<KSAnnotation>()?.forEach { indexAnnotations += it }
            }
        }

        for (ann in indexAnnotations) {
            val idxName = ann.arguments.firstOrNull { it.name?.asString() == "value" }?.value as? String ?: ""
            val columns = (ann.arguments.firstOrNull { it.name?.asString() == "columns" }?.value as? kotlin.collections.List<*>)?.filterIsInstance<String>() ?: emptyList()
            val unique = ann.arguments.firstOrNull { it.name?.asString() == "unique" }?.value as? Boolean ?: false
            if (columns.isNotEmpty()) {
                val colsBlock = CodeBlock.builder().add("\$T.of(", java.util.List::class.java)
                for (i in columns.indices) {
                    if (i > 0) colsBlock.add(", ")
                    colsBlock.add("\$S", columns[i])
                }
                colsBlock.add(")")
                staticBlock.addStatement(
                    "INSTANCE.addIndex(new \$T.IndexDef(\$S, \$L, \$L))",
                    Table::class.java, idxName, colsBlock.build(), unique
                )
            }
        }
    }

    private fun generatePrimitiveAccessors(
        classBuilder: TypeSpec.Builder,
        entityClass: ClassName,
        properties: kotlin.collections.List<PropertyData>
    ) {
        // newInstance()
        classBuilder.addMethod(
            MethodSpec.methodBuilder("newInstance")
                .addAnnotation(Override::class.java)
                .addModifiers(Modifier.PUBLIC)
                .returns(entityClass)
                .addStatement("return new \$T()", entityClass)
                .build()
        )

        // 8 Primitive Getters and Setters + Object
        generatePrimitiveMethod(classBuilder, entityClass, properties, PrimitiveKind.INT, TypeName.INT, "getInt", "setInt")
        generatePrimitiveMethod(classBuilder, entityClass, properties, PrimitiveKind.LONG, TypeName.LONG, "getLong", "setLong")
        generatePrimitiveMethod(classBuilder, entityClass, properties, PrimitiveKind.BOOLEAN, TypeName.BOOLEAN, "getBoolean", "setBoolean")
        generatePrimitiveMethod(classBuilder, entityClass, properties, PrimitiveKind.DOUBLE, TypeName.DOUBLE, "getDouble", "setDouble")
        generatePrimitiveMethod(classBuilder, entityClass, properties, PrimitiveKind.FLOAT, TypeName.FLOAT, "getFloat", "setFloat")
        generatePrimitiveMethod(classBuilder, entityClass, properties, PrimitiveKind.SHORT, TypeName.SHORT, "getShort", "setShort")
        generatePrimitiveMethod(classBuilder, entityClass, properties, PrimitiveKind.BYTE, TypeName.BYTE, "getByte", "setByte")
        generatePrimitiveMethod(classBuilder, entityClass, properties, PrimitiveKind.CHAR, TypeName.CHAR, "getChar", "setChar")

        // setObject & getObject
        generateObjectAccessors(classBuilder, entityClass, properties)
    }

    private fun generatePrimitiveMethod(
        classBuilder: TypeSpec.Builder,
        entityClass: ClassName,
        properties: kotlin.collections.List<PropertyData>,
        kind: PrimitiveKind,
        primType: TypeName,
        getterName: String,
        setterName: String
    ) {
        // Getter
        val getMb = MethodSpec.methodBuilder(getterName)
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .returns(primType)
            .addParameter(entityClass, "instance")
            .addParameter(TypeName.INT, "colIndex")

        val getBlock = CodeBlock.builder().beginControlFlow("switch (colIndex)")
        for (i in properties.indices) {
            val prop = properties[i]
            if (prop.fieldType == primType) {
                val getterCall = "instance.get${prop.propName.replaceFirstChar { it.uppercaseChar() }}()"
                getBlock.addStatement("case \$L: return \$L", i, getterCall)
            }
        }
        getBlock.add("default:\n").indent()
            .addStatement("throw new \$T(\$S + colIndex)", UnsupportedOperationException::class.java, "$getterName not supported for colIndex ")
            .unindent()
        getBlock.endControlFlow()
        getMb.addCode(getBlock.build())
        classBuilder.addMethod(getMb.build())

        // Setter
        val setMb = MethodSpec.methodBuilder(setterName)
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(entityClass, "instance")
            .addParameter(TypeName.INT, "colIndex")
            .addParameter(primType, "value")

        val setBlock = CodeBlock.builder().beginControlFlow("switch (colIndex)")
        for (i in properties.indices) {
            val prop = properties[i]
            if (prop.fieldType == primType) {
                val setterCall = "instance.set${prop.propName.replaceFirstChar { it.uppercaseChar() }}(value)"
                setBlock.addStatement("case \$L: \$L; break", i, setterCall)
            }
        }
        setBlock.add("default:\n").indent()
            .addStatement("throw new \$T(\$S + colIndex)", UnsupportedOperationException::class.java, "$setterName not supported for colIndex ")
            .unindent()
        setBlock.endControlFlow()
        setMb.addCode(setBlock.build())
        classBuilder.addMethod(setMb.build())
    }

    private fun generateObjectAccessors(
        classBuilder: TypeSpec.Builder,
        entityClass: ClassName,
        properties: kotlin.collections.List<PropertyData>
    ) {
        // getObject
        val getMb = MethodSpec.methodBuilder("getObject")
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .returns(Object::class.java)
            .addParameter(entityClass, "instance")
            .addParameter(TypeName.INT, "colIndex")

        val getBlock = CodeBlock.builder().beginControlFlow("switch (colIndex)")
        for (i in properties.indices) {
            val prop = properties[i]
            val getterCall = "instance.get${prop.propName.replaceFirstChar { it.uppercaseChar() }}()"
            getBlock.addStatement("case \$L: return \$L", i, getterCall)
        }
        getBlock.add("default:\n").indent()
            .addStatement("throw new \$T(\$S + colIndex)", UnsupportedOperationException::class.java, "getObject not supported for colIndex ")
            .unindent()
        getBlock.endControlFlow()
        getMb.addCode(getBlock.build())
        classBuilder.addMethod(getMb.build())

        // setObject
        val setMb = MethodSpec.methodBuilder("setObject")
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(entityClass, "instance")
            .addParameter(TypeName.INT, "colIndex")
            .addParameter(Object::class.java, "value")

        val setBlock = CodeBlock.builder().beginControlFlow("switch (colIndex)")
        for (i in properties.indices) {
            val prop = properties[i]
            val setterName = "set${prop.propName.replaceFirstChar { it.uppercaseChar() }}"
            setBlock.add("case \$L:\n", i).indent()
            if (prop.columnType != prop.boxedType) {
                setBlock.beginControlFlow("if (value != null && \$T.class.isInstance(value))", prop.boxedType)
                setBlock.addStatement("instance.\$L((\$T) value)", setterName, prop.fieldType)
                setBlock.endControlFlow()
            } else if (prop.fieldType.isPrimitive) {
                setBlock.beginControlFlow("if (value != null)")
                setBlock.addStatement("instance.\$L((\$T) value)", setterName, prop.fieldType)
                setBlock.endControlFlow()
            } else {
                setBlock.addStatement("instance.\$L((\$T) value)", setterName, prop.fieldType)
            }
            setBlock.addStatement("break")
            setBlock.unindent()
        }
        setBlock.add("default:\n").indent()
            .addStatement("throw new \$T(\$S + colIndex)", UnsupportedOperationException::class.java, "setObject not supported for colIndex ")
            .unindent()
        setBlock.endControlFlow()
        setMb.addCode(setBlock.build())
        classBuilder.addMethod(setMb.build())
    }
}

private fun KSType.toJavaPoet(box: Boolean = false): TypeName {
    val decl = declaration
    val qName = decl.qualifiedName?.asString() ?: return TypeName.OBJECT
    if (!box) {
        when (qName) {
            "kotlin.Int" -> return TypeName.INT
            "kotlin.Long" -> return TypeName.LONG
            "kotlin.Boolean" -> return TypeName.BOOLEAN
            "kotlin.Double" -> return TypeName.DOUBLE
            "kotlin.Float" -> return TypeName.FLOAT
            "kotlin.Short" -> return TypeName.SHORT
            "kotlin.Byte" -> return TypeName.BYTE
            "kotlin.Char" -> return TypeName.CHAR
            "kotlin.Unit" -> return TypeName.VOID
        }
    }
    return when (qName) {
        "kotlin.Int", "java.lang.Integer" -> ClassName.get(Integer::class.java)
        "kotlin.Long", "java.lang.Long" -> ClassName.get(java.lang.Long::class.java)
        "kotlin.Boolean", "java.lang.Boolean" -> ClassName.get(java.lang.Boolean::class.java)
        "kotlin.Double", "java.lang.Double" -> ClassName.get(java.lang.Double::class.java)
        "kotlin.Float", "java.lang.Float" -> ClassName.get(java.lang.Float::class.java)
        "kotlin.Short", "java.lang.Short" -> ClassName.get(java.lang.Short::class.java)
        "kotlin.Byte", "java.lang.Byte" -> ClassName.get(java.lang.Byte::class.java)
        "kotlin.Char", "java.lang.Character" -> ClassName.get(java.lang.Character::class.java)
        "kotlin.String", "java.lang.String" -> ClassName.get(String::class.java)
        else -> {
            val pkg = decl.packageName.asString()
            val simple = decl.simpleName.asString()
            ClassName.get(pkg, simple)
        }
    }
}
