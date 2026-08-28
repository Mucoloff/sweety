package dev.sweety.sql4j.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({
    "dev.sweety.sql4j.api.obj.Table.Info",
    "dev.sweety.sql4j.api.obj.Column.Info",
    "dev.sweety.sql4j.api.obj.annotation.ManyToOne",
    "dev.sweety.sql4j.api.obj.annotation.OneToMany",
    "dev.sweety.sql4j.api.obj.annotation.ManyToMany",
    "dev.sweety.sql4j.api.annotation.Sql4jRepository",
    "dev.sweety.sql4j.api.query.Query.Info"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@AutoService(Processor.class)
public class SQL4JProcessor extends AbstractProcessor {

    private static final String TABLE_INFO = "dev.sweety.sql4j.api.obj.Table.Info";
    private static final String COLUMN_INFO = "dev.sweety.sql4j.api.obj.Column.Info";
    private static final String MANY_TO_ONE = "dev.sweety.sql4j.api.obj.annotation.ManyToOne";
    private static final String ONE_TO_MANY = "dev.sweety.sql4j.api.obj.annotation.OneToMany";
    private static final String MANY_TO_MANY = "dev.sweety.sql4j.api.obj.annotation.ManyToMany";
    private static final String SQL4J_REPOSITORY = "dev.sweety.sql4j.api.annotation.Sql4jRepository";
    private static final String QUERY_ANNOTATION = "dev.sweety.sql4j.api.query.Query.Info";
    private static final String CACHE_EVICT_ANNOTATION = "dev.sweety.sql4j.api.annotation.CacheEvict";
    
    private static final String TABLE_REGISTRY = "dev.sweety.sql4j.api.obj.table.TableRegistry";
    private static final String COLUMN = "dev.sweety.sql4j.api.obj.Column";
    private static final String TABLE_ACCESSOR = "dev.sweety.sql4j.api.obj.TableAccessor";
    private static final String TABLE_RELATION = "dev.sweety.sql4j.api.obj.Table.Relation";

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        TypeElement tableInfoType = processingEnv.getElementUtils().getTypeElement(TABLE_INFO);
        if (tableInfoType != null) {
            for (Element element : roundEnv.getElementsAnnotatedWith(tableInfoType)) {
                if (element.getKind() == ElementKind.CLASS) {
                    try {
                        generateMirrorClass((TypeElement) element);
                    } catch (IOException e) {
                        if (!e.getMessage().contains("Attempt to recreate a file")) {
                            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate table class: " + e.getMessage());
                        }
                    }
                }
            }
        }
        
        TypeElement repoType = processingEnv.getElementUtils().getTypeElement(SQL4J_REPOSITORY);
        if (repoType != null) {
            for (Element element : roundEnv.getElementsAnnotatedWith(repoType)) {
                if (element.getKind() == ElementKind.INTERFACE) {
                    try {
                        generateRepositoryImpl((TypeElement) element);
                    } catch (IOException e) {
                        if (!e.getMessage().contains("Attempt to recreate a file")) {
                            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate repository: " + e.getMessage());
                        }
                    }
                }
            }
        }
        return true;
    }

    private void generateMirrorClass(TypeElement typeElement) throws IOException {
        String packageName = processingEnv.getElementUtils().getPackageOf(typeElement).getQualifiedName().toString();
        String simpleName = typeElement.getSimpleName().toString();
        
        String mirrorName;
        Element enclosing = typeElement.getEnclosingElement();
        StringBuilder mirrorNameBuilder = new StringBuilder(simpleName + "Table");
        while (enclosing != null && enclosing.getKind() == ElementKind.CLASS) {
            mirrorNameBuilder.insert(0, enclosing.getSimpleName() + "_");
            enclosing = enclosing.getEnclosingElement();
        }
        mirrorName = mirrorNameBuilder.toString();

        ClassName entityClass = ClassName.get(typeElement);
        ClassName columnClass = ClassName.bestGuess(COLUMN);
        ClassName registryClass = ClassName.bestGuess(TABLE_REGISTRY);
        ClassName accessorInterface = ClassName.bestGuess(TABLE_ACCESSOR);
        ClassName relationClass = ClassName.bestGuess(TABLE_RELATION);
        ClassName fetchTypeClass = ClassName.get("dev.sweety.sql4j.api.obj.annotation", "FetchType");

        TypeSpec.Builder mirrorBuilder = TypeSpec.classBuilder(mirrorName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(ParameterizedTypeName.get(ClassName.get("dev.sweety.sql4j.api.obj", "Table"), entityClass))
                .addSuperinterface(ParameterizedTypeName.get(accessorInterface, entityClass))
                .addJavadoc("Generated by SQL4J Processor. Do not modify.\n");

        mirrorBuilder.addField(FieldSpec.builder(ClassName.get(packageName, mirrorName), "INSTANCE")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .build());

        // Extract table name
        String tableName = simpleName.toLowerCase();
        for (AnnotationMirror am : typeElement.getAnnotationMirrors()) {
            if (am.getAnnotationType().toString().equals(TABLE_INFO)) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : am.getElementValues().entrySet()) {
                    if (entry.getKey().getSimpleName().toString().equals("name") && !entry.getValue().getValue().toString().isEmpty()) {
                        tableName = entry.getValue().getValue().toString();
                    }
                }
            }
        }

        mirrorBuilder.addField(FieldSpec.builder(String.class, "TABLE_NAME")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", tableName)
                .build());

        List<FieldData> fields = new ArrayList<>();
        List<RelationData> relations = new ArrayList<>();
        List<IndexData> classIndices = new ArrayList<>();

        // Extract class-level @Index and @Indexes
        for (AnnotationMirror am : typeElement.getAnnotationMirrors()) {
            String annType = am.getAnnotationType().toString();
            if (annType.equals("dev.sweety.sql4j.api.obj.annotation.Index")) {
                parseIndexAnnotation(am, tableName, classIndices);
            } else if (annType.equals("dev.sweety.sql4j.api.obj.annotation.Indexes")) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : am.getElementValues().entrySet()) {
                    if (entry.getKey().getSimpleName().toString().equals("value")) {
                        @SuppressWarnings("unchecked")
                        List<? extends AnnotationValue> list = (List<? extends AnnotationValue>) entry.getValue().getValue();
                        for (AnnotationValue val : list) {
                            if (val.getValue() instanceof AnnotationMirror subAm) {
                                parseIndexAnnotation(subAm, tableName, classIndices);
                            }
                        }
                    }
                }
            }
        }

        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) enclosed;
                String fieldName = field.getSimpleName().toString();
                boolean isPrivate = field.getModifiers().contains(Modifier.PRIVATE);

                // Column detection
                AnnotationMirror colAnn = getAnnotation(field, COLUMN_INFO);
                AnnotationMirror mtoAnn = getAnnotation(field, MANY_TO_ONE);
                AnnotationMirror otmAnn = getAnnotation(field, ONE_TO_MANY);
                AnnotationMirror mtmAnn = getAnnotation(field, MANY_TO_MANY);

                if (colAnn != null || mtoAnn != null) {
                    String colName = fieldName;
                    if (colAnn != null) {
                        String annName = getAnnotationValue(colAnn, "name");
                        if (annName != null && !annName.isEmpty()) colName = annName;
                    } else if (mtoAnn != null) {
                        String annName = getAnnotationValue(mtoAnn, "columnName");
                        colName = (annName != null && !annName.isEmpty()) ? annName : fieldName + "_id";
                    }

                    TypeName fieldType = TypeName.get(field.asType());
                    TypeName boxedFieldType = fieldType.isPrimitive() ? fieldType.box() : fieldType;
                    TypeName columnType = boxedFieldType;
                    
                    if (mtoAnn != null) {
                        columnType = getPrimaryKeyType(field);
                    }

                    boolean isPk = colAnn != null && "true".equals(getAnnotationValue(colAnn, "primaryKey"));
                    boolean isAuto = colAnn != null && "true".equals(getAnnotationValue(colAnn, "autoIncrement"));
                    boolean isNull = colAnn == null || "true".equals(getAnnotationValue(colAnn, "nullable"));
                    boolean isSoftDelete = getAnnotation(field, "dev.sweety.sql4j.api.obj.annotation.SoftDelete") != null;

                    AnnotationMirror indexAnn = getAnnotation(field, "dev.sweety.sql4j.api.obj.annotation.Index");
                    String indexName = null;
                    boolean isUniqueIdx = false;
                    if (indexAnn != null) {
                        String idxAnnName = getAnnotationValue(indexAnn, "name");
                        indexName = (idxAnnName != null && !idxAnnName.isEmpty()) ? idxAnnName : "idx_" + tableName + "_" + colName;
                        isUniqueIdx = "true".equals(getAnnotationValue(indexAnn, "unique"));
                    }

                    FieldData fd = new FieldData(fieldName, colName, fieldType, boxedFieldType, columnType, isPrivate, isPk, isAuto, isNull, isSoftDelete, indexName, isUniqueIdx);
                    fields.add(fd);

                    // Static Column reference
                    mirrorBuilder.addField(FieldSpec.builder(ParameterizedTypeName.get(columnClass, columnType), fieldName.toUpperCase())
                            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                            .build());

                    // Field cache for both private and relation fields
                    mirrorBuilder.addField(FieldSpec.builder(Field.class, "FIELD_" + fieldName)
                            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                            .initializer("getPrivateField($T.class, $S)", entityClass, fieldName)
                            .build());

                    // Individual Accessors
                    mirrorBuilder.addMethod(buildGetter(entityClass, fd));
                    mirrorBuilder.addMethod(buildSetter(entityClass, fd));
                }

                // Relation detection
                if (mtoAnn != null || otmAnn != null || mtmAnn != null) {
                    if (colAnn == null && mtoAnn == null) {
                        // Field cache for non-column relations (e.g. OneToMany)
                        mirrorBuilder.addField(FieldSpec.builder(Field.class, "FIELD_" + fieldName)
                                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                                .initializer("getPrivateField($T.class, $S)", entityClass, fieldName)
                                .build());
                    }

                    String relType;
                    String mappedBy = null;
                    if (mtoAnn != null) relType = "MANY_TO_ONE";
                    else if (otmAnn != null) {
                        relType = "ONE_TO_MANY";
                        mappedBy = getAnnotationValue(otmAnn, "mappedBy");
                    } else {
                        relType = "MANY_TO_MANY";
                    }

                    TypeName targetClass = getTargetClass(field);
                    String colRef = (mtoAnn != null) ? fieldName.toUpperCase() : "null";

                    String fetchTypeStr = "LAZY";
                    if (otmAnn != null) {
                        fetchTypeStr = normalizeFetchType(getAnnotationValue(otmAnn, "fetchType"));
                    } else if (mtmAnn != null) {
                        fetchTypeStr = normalizeFetchType(getAnnotationValue(mtmAnn, "fetchType"));
                    }

                    String relConst = fieldName.toUpperCase() + "_REL";
                    mirrorBuilder.addField(FieldSpec.builder(relationClass, relConst)
                            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                            .build());

                    relations.add(new RelationData(relConst, relType, fieldName, targetClass, mappedBy, colRef, fetchTypeStr));
                }
            }
        }

        // Validate: at least one primary key
        boolean hasPrimaryKey = fields.stream().anyMatch(f -> f.isPrimaryKey);
        if (!hasPrimaryKey) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Entity '" + simpleName + "' must have at least one primary key column " +
                    "(@Column.Info(primaryKey = true))", typeElement);
            return;
        }

        // Validate: no duplicate column names
        Map<String, Long> colNameCounts = fields.stream()
                .collect(Collectors.groupingBy(f -> f.colName, Collectors.counting()));
        for (Map.Entry<String, Long> entry : colNameCounts.entrySet()) {
            if (entry.getValue() > 1) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Duplicate column name '" + entry.getKey() + "' in entity '" + simpleName + "'", typeElement);
                return;
            }
        }

        // Static SQL Generation
        String allCols = fields.stream().map(f -> f.colName).collect(Collectors.joining(", "));
        String insertCols = fields.stream().filter(f -> !f.isAutoInc).map(f -> f.colName).collect(Collectors.joining(", "));
        String insertPlaceholders = fields.stream().filter(f -> !f.isAutoInc).map(ignored -> "?").collect(Collectors.joining(", "));

        mirrorBuilder.addField(FieldSpec.builder(String.class, "SELECT_ALL")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", "SELECT " + allCols + " FROM " + tableName)
                .build());

        mirrorBuilder.addField(FieldSpec.builder(String.class, "INSERT")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", "INSERT INTO " + tableName + " (" + insertCols + ") VALUES (" + insertPlaceholders + ")")
                .build());

        // Public alias for API consumers
        mirrorBuilder.addField(FieldSpec.builder(String.class, "INSERT_SQL")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("INSERT")
                .build());

        String updateSet = fields.stream().filter(f -> !f.isPrimaryKey && !f.isAutoInc).map(f -> f.colName + " = ?").collect(Collectors.joining(", "));
        String pkWhere = fields.stream().filter(f -> f.isPrimaryKey).map(f -> f.colName + " = ?").collect(Collectors.joining(" AND "));

        mirrorBuilder.addField(FieldSpec.builder(String.class, "UPDATE")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", "UPDATE " + tableName + " SET " + updateSet + " WHERE " + pkWhere)
                .build());

        mirrorBuilder.addField(FieldSpec.builder(String.class, "DELETE")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", "DELETE FROM " + tableName + " WHERE " + pkWhere)
                .build());

        String selectByPkSql = "SELECT " + allCols + " FROM " + tableName + " WHERE " + pkWhere;
        mirrorBuilder.addField(FieldSpec.builder(String.class, "SELECT_BY_PK_SQL")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", selectByPkSql)
                .build());

        // Private Constructor
        mirrorBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addStatement("super($T.class, TABLE_NAME)", entityClass)
                .addStatement("setAccessor(this)")
                .build());

        // Static Initialization
        CodeBlock.Builder staticBlock = CodeBlock.builder()
                .addStatement("INSTANCE = new $L()", mirrorName)
                .addStatement("$T.getDefault().register(INSTANCE)", registryClass);
        
        for (FieldData fd : fields) {
            String colConst = fd.fieldName.toUpperCase();
            TypeName instType = (fd.columnType != fd.boxedType) ? fd.columnType : fd.fieldType;
            staticBlock.addStatement("$L = new $T<>(INSTANCE, $S, $T.class, $L, $L, $L)", 
                    colConst, columnClass, fd.colName, instType, fd.isPrimaryKey, fd.isAutoInc, fd.isNullable);
            if (fd.isSoftDelete) staticBlock.addStatement("$L.setSoftDelete(true)", colConst);
            if (fd.columnType != fd.boxedType) staticBlock.addStatement("$L.setRelation(true)", colConst);
            if (fd.indexName != null) {
                staticBlock.addStatement("$L.setIndexName($S)", colConst, fd.indexName);
                if (fd.isUniqueIndex) staticBlock.addStatement("$L.setUnique(true)", colConst);
                staticBlock.addStatement("INSTANCE.addIndex(new $T.IndexDef($S, $T.of($S), $L))",
                        ClassName.get("dev.sweety.sql4j.api.obj", "Table"), fd.indexName, List.class, fd.colName, fd.isUniqueIndex);
            }
            staticBlock.addStatement("INSTANCE.addColumn($L)", colConst);
        }
        for (IndexData id : classIndices) {
            CodeBlock.Builder colsBlock = CodeBlock.builder().add("$T.of(", List.class);
            for (int i = 0; i < id.columns.size(); i++) {
                if (i > 0) colsBlock.add(", ");
                colsBlock.add("$S", id.columns.get(i));
            }
            colsBlock.add(")");
            staticBlock.addStatement("INSTANCE.addIndex(new $T.IndexDef($S, $L, $L))",
                    ClassName.get("dev.sweety.sql4j.api.obj", "Table"), id.name, colsBlock.build(), id.unique);
        }
        for (RelationData rd : relations) {
            if ("MANY_TO_ONE".equals(rd.type())) {
                staticBlock.addStatement("$L = new $T($T.Type.MANY_TO_ONE, FIELD_$L, $T.class, null, null, $L, $T.$L)",
                        rd.constName, relationClass, relationClass, rd.fieldName, rd.targetClass, rd.colRef, fetchTypeClass, rd.fetchType);
            } else if ("ONE_TO_MANY".equals(rd.type())) {
                staticBlock.addStatement("$L = new $T($T.Type.ONE_TO_MANY, FIELD_$L, $T.class, $S, null, null, $T.$L)",
                        rd.constName, relationClass, relationClass, rd.fieldName, rd.targetClass, rd.mappedBy, fetchTypeClass, rd.fetchType);
            } else {
                staticBlock.addStatement("$L = new $T($T.Type.MANY_TO_MANY, FIELD_$L, $T.class, $L, null, $L, $T.$L)",
                        rd.constName, relationClass, relationClass, rd.fieldName, rd.targetClass, rd.mappedBy, rd.colRef, fetchTypeClass, rd.fetchType);
            }
            staticBlock.addStatement("INSTANCE.addRelation($L)", rd.constName);
        }
        
        staticBlock.addStatement("INSTANCE.markInitialized()");
        mirrorBuilder.addStaticBlock(staticBlock.build());

        mirrorBuilder.addMethod(MethodSpec.methodBuilder("getSelectAllSql")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC).returns(String.class)
                .addStatement("return SELECT_ALL").build());

        mirrorBuilder.addMethod(MethodSpec.methodBuilder("getInsertSql")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC).returns(String.class)
                .addStatement("return INSERT").build());

        mirrorBuilder.addMethod(MethodSpec.methodBuilder("newInstance")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC).returns(entityClass)
                .addStatement("return new $T()", entityClass).build());

        // Primitive & Object Dispatchers (Zero-Boxing with UnsupportedOperationException)
        mirrorBuilder.addMethod(buildPrimitiveDispatcher("getBoolean", TypeName.BOOLEAN, TypeName.BOOLEAN, entityClass, fields, false));
        mirrorBuilder.addMethod(buildPrimitiveDispatcher("setBoolean", TypeName.BOOLEAN, TypeName.BOOLEAN, entityClass, fields, true));

        mirrorBuilder.addMethod(buildPrimitiveDispatcher("getByte", TypeName.BYTE, TypeName.BYTE, entityClass, fields, false));
        mirrorBuilder.addMethod(buildPrimitiveDispatcher("setByte", TypeName.BYTE, TypeName.BYTE, entityClass, fields, true));

        mirrorBuilder.addMethod(buildPrimitiveDispatcher("getShort", TypeName.SHORT, TypeName.SHORT, entityClass, fields, false));
        mirrorBuilder.addMethod(buildPrimitiveDispatcher("setShort", TypeName.SHORT, TypeName.SHORT, entityClass, fields, true));

        mirrorBuilder.addMethod(buildPrimitiveDispatcher("getInt", TypeName.INT, TypeName.INT, entityClass, fields, false));
        mirrorBuilder.addMethod(buildPrimitiveDispatcher("setInt", TypeName.INT, TypeName.INT, entityClass, fields, true));

        mirrorBuilder.addMethod(buildPrimitiveDispatcher("getLong", TypeName.LONG, TypeName.LONG, entityClass, fields, false));
        mirrorBuilder.addMethod(buildPrimitiveDispatcher("setLong", TypeName.LONG, TypeName.LONG, entityClass, fields, true));

        mirrorBuilder.addMethod(buildPrimitiveDispatcher("getFloat", TypeName.FLOAT, TypeName.FLOAT, entityClass, fields, false));
        mirrorBuilder.addMethod(buildPrimitiveDispatcher("setFloat", TypeName.FLOAT, TypeName.FLOAT, entityClass, fields, true));

        mirrorBuilder.addMethod(buildPrimitiveDispatcher("getDouble", TypeName.DOUBLE, TypeName.DOUBLE, entityClass, fields, false));
        mirrorBuilder.addMethod(buildPrimitiveDispatcher("setDouble", TypeName.DOUBLE, TypeName.DOUBLE, entityClass, fields, true));

        mirrorBuilder.addMethod(buildPrimitiveDispatcher("getChar", TypeName.CHAR, TypeName.CHAR, entityClass, fields, false));
        mirrorBuilder.addMethod(buildPrimitiveDispatcher("setChar", TypeName.CHAR, TypeName.CHAR, entityClass, fields, true));

        mirrorBuilder.addMethod(buildObjectDispatcher("getObject", entityClass, fields, false));
        mirrorBuilder.addMethod(buildObjectDispatcher("setObject", entityClass, fields, true));

        // Legacy string-based dispatchers
        mirrorBuilder.addMethod(buildDispatcher("setFieldValue", entityClass, fields, true));
        mirrorBuilder.addMethod(buildDispatcher("getFieldValue", entityClass, fields, false));

        // Builder class
        mirrorBuilder.addType(buildBuilder(entityClass, fields));
        mirrorBuilder.addMethod(MethodSpec.methodBuilder("builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.get("", mirrorName + ".Builder"))
                .addStatement("return new Builder()")
                .build());

        // Helper for private fields
        mirrorBuilder.addMethod(MethodSpec.methodBuilder("getPrivateField")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addParameter(Class.class, "clazz")
                .addParameter(String.class, "name")
                .returns(Field.class)
                .addCode("""
                        try {
                            Field f = clazz.getDeclaredField(name);
                            f.setAccessible(true);
                            return f;
                        } catch (Exception e) { throw new RuntimeException(e); }
                        """)
                .build());

        JavaFile javaFile = JavaFile.builder(packageName, mirrorBuilder.build()).build();
        javaFile.writeTo(processingEnv.getFiler());
    }

    private TypeSpec buildBuilder(ClassName entityClass, List<FieldData> fields) {
        TypeSpec.Builder builder = TypeSpec.classBuilder("Builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);

        builder.addField(FieldSpec.builder(entityClass, "instance", Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T()", entityClass)
                .build());

        for (FieldData fd : fields) {
            builder.addMethod(MethodSpec.methodBuilder(fd.fieldName)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(fd.fieldType, "value")
                    .returns(ClassName.get("", "Builder"))
                    .addStatement("set_$L(instance, value)", fd.fieldName)
                    .addStatement("return this")
                    .build());
        }

        builder.addMethod(MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(entityClass)
                .addStatement("return instance")
                .build());

        return builder.build();
    }

    private MethodSpec buildGetter(ClassName entityClass, FieldData fd) {
        MethodSpec.Builder mb = MethodSpec.methodBuilder("get_" + fd.fieldName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(entityClass, "instance")
                .returns(fd.fieldType);
        mb.beginControlFlow("try")
          .addStatement("return ($T) FIELD_$L.get(instance)", fd.boxedType, fd.fieldName)
          .nextControlFlow("catch (Exception e)")
          .addStatement("throw new RuntimeException(e)")
          .endControlFlow();
        return mb.build();
    }

    private MethodSpec buildSetter(ClassName entityClass, FieldData fd) {
        MethodSpec.Builder mb = MethodSpec.methodBuilder("set_" + fd.fieldName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(entityClass, "instance")
                .addParameter(fd.fieldType, "value");
        mb.beginControlFlow("try")
          .addStatement("FIELD_$L.set(instance, value)", fd.fieldName)
          .nextControlFlow("catch (Exception e)")
          .addStatement("throw new RuntimeException(e)")
          .endControlFlow();
        return mb.build();
    }

    private MethodSpec buildDispatcher(String name, ClassName entityClass, List<FieldData> fields, boolean isSetter) {
        MethodSpec.Builder mb = MethodSpec.methodBuilder(name)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(entityClass, "instance")
                .addParameter(String.class, "colName");
        
        if (isSetter) mb.addParameter(Object.class, "value");
        else mb.returns(Object.class);

        CodeBlock.Builder block = CodeBlock.builder().beginControlFlow("switch (colName)");
        for (FieldData fd : fields) {
            block.add("case $S:\n", fd.colName);
            block.indent();
            if (isSetter) {
                if (fd.columnType != fd.boxedType) {
                    // It's a relation column, only set if type matches
                    block.beginControlFlow("if (value != null && $T.class.isInstance(value))", fd.boxedType);
                    block.addStatement("set_$L(instance, ($T) value)", fd.fieldName, fd.fieldType);
                    block.endControlFlow();
                } else if (fd.fieldType.isPrimitive()) {
                    // A primitive field (boolean/int/long/...) can't hold null - casting a null Object
                    // here would NPE on unboxing (e.g. a legacy row with SQL NULL in a nullable=false
                    // column whose migration added a DDL default but never backfilled existing rows).
                    // Skip the set: the field keeps its Java-default value (false/0/0L/...), which
                    // matches the declared @Column.Info default for every current nullable=false primitive.
                    block.beginControlFlow("if (value != null)");
                    block.addStatement("set_$L(instance, ($T) value)", fd.fieldName, fd.fieldType);
                    block.endControlFlow();
                } else {
                    block.addStatement("set_$L(instance, ($T) value)", fd.fieldName, fd.fieldType);
                }
                block.addStatement("break");
            } else {
                block.addStatement("return get_$L(instance)", fd.fieldName);
            }
            block.unindent();
        }
        if (!isSetter) block.add("default:\n").indent().addStatement("return null").unindent();
        block.endControlFlow();
        return mb.addCode(block.build()).build();
    }

    private MethodSpec buildPrimitiveDispatcher(String name, TypeName primType, TypeName boxedType, ClassName entityClass, List<FieldData> fields, boolean isSetter) {
        MethodSpec.Builder mb = MethodSpec.methodBuilder(name)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(entityClass, "instance")
                .addParameter(TypeName.INT, "colIndex");

        if (isSetter) mb.addParameter(primType, "value");
        else mb.returns(primType);

        CodeBlock.Builder block = CodeBlock.builder().beginControlFlow("switch (colIndex)");
        boolean hasMatchingFields = false;

        for (int i = 0; i < fields.size(); i++) {
            FieldData fd = fields.get(i);
            if (fd.fieldType.equals(primType)) {
                hasMatchingFields = true;
                block.add("case $L:\n", i);
                block.indent();
                if (isSetter) {
                    block.addStatement("set_$L(instance, value)", fd.fieldName);
                    block.addStatement("break");
                } else {
                    block.addStatement("return get_$L(instance)", fd.fieldName);
                }
                block.unindent();
            }
        }

        block.add("default:\n").indent()
                .addStatement("throw new $T($S + colIndex)", UnsupportedOperationException.class, name + " not supported for colIndex ")
                .unindent();
        block.endControlFlow();

        return mb.addCode(block.build()).build();
    }

    private MethodSpec buildObjectDispatcher(String name, ClassName entityClass, List<FieldData> fields, boolean isSetter) {
        MethodSpec.Builder mb = MethodSpec.methodBuilder(name)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(entityClass, "instance")
                .addParameter(TypeName.INT, "colIndex");

        if (isSetter) mb.addParameter(Object.class, "value");
        else mb.returns(Object.class);

        CodeBlock.Builder block = CodeBlock.builder().beginControlFlow("switch (colIndex)");

        for (int i = 0; i < fields.size(); i++) {
            FieldData fd = fields.get(i);
            block.add("case $L:\n", i);
            block.indent();
            if (isSetter) {
                if (fd.columnType != fd.boxedType) {
                    // It's a relation column, only set if type matches the Entity class
                    block.beginControlFlow("if (value != null && $T.class.isInstance(value))", fd.boxedType);
                    block.addStatement("set_$L(instance, ($T) value)", fd.fieldName, fd.fieldType);
                    block.endControlFlow();
                } else if (fd.fieldType.isPrimitive()) {
                    // Setting Object value to primitive field (boxing bridge)
                    block.beginControlFlow("if (value != null)");
                    block.addStatement("set_$L(instance, ($T) value)", fd.fieldName, fd.fieldType);
                    block.endControlFlow();
                } else {
                    block.addStatement("set_$L(instance, ($T) value)", fd.fieldName, fd.fieldType);
                }
                block.addStatement("break");
            } else {
                block.addStatement("return get_$L(instance)", fd.fieldName);
            }
            block.unindent();
        }

        block.add("default:\n").indent()
                .addStatement("throw new $T($S + colIndex)", UnsupportedOperationException.class, name + " not supported for colIndex ")
                .unindent();
        block.endControlFlow();

        return mb.addCode(block.build()).build();
    }

    private AnnotationMirror getAnnotation(Element element, String typeName) {
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            if (am.getAnnotationType().toString().equals(typeName)) return am;
        }
        return null;
    }

    private String getAnnotationValue(AnnotationMirror am, String key) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : am.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().toString().equals(key)) return entry.getValue().getValue().toString();
        }
        return null;
    }

    /** Resolves {@code OneToMany}/{@code ManyToMany} {@code fetchType} enum for generated . */
    private static String normalizeFetchType(String raw) {
        if (raw == null) return "LAZY";
        if (raw.endsWith("EAGER")) return "EAGER";
        return "LAZY";
    }

    private TypeName getTargetClass(VariableElement field) {
        TypeMirror type = field.asType();
        if (type instanceof DeclaredType dt) {
            List<? extends TypeMirror> typeArgs = dt.getTypeArguments();
            if (!typeArgs.isEmpty()) return TypeName.get(typeArgs.getFirst());
        }
        return TypeName.get(type);
    }

    private TypeName getPrimaryKeyType(VariableElement field) {
        TypeMirror typeMirror = field.asType();
        if (typeMirror instanceof DeclaredType) {
            TypeElement typeElement = (TypeElement) ((DeclaredType) typeMirror).asElement();
            for (Element enclosed : typeElement.getEnclosedElements()) {
                if (enclosed.getKind() == ElementKind.FIELD) {
                    AnnotationMirror colAnn = getAnnotation(enclosed, COLUMN_INFO);
                    if (colAnn != null && "true".equals(getAnnotationValue(colAnn, "primaryKey"))) {
                        TypeName tn = TypeName.get(enclosed.asType());
                        return tn.isPrimitive() ? tn.box() : tn;
                    }
                }
            }
        }
        return ClassName.get(Integer.class); // Fallback
    }

    private void generateRepositoryImpl(TypeElement typeElement) throws IOException {
        String packageName = processingEnv.getElementUtils().getPackageOf(typeElement).getQualifiedName().toString();
        String interfaceName = typeElement.getSimpleName().toString();
        String implName = interfaceName + "Impl";

        AnnotationMirror repoAnn = getAnnotation(typeElement, SQL4J_REPOSITORY);
        // Extract entity type from annotation
        TypeMirror entityType = null;
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : repoAnn.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().toString().equals("entity")) {
                entityType = (TypeMirror) entry.getValue().getValue();
                break;
            }
        }

        if (entityType == null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Entity not specified in @Sql4jRepository", typeElement);
            return;
        }

        TypeName entityTypeName = TypeName.get(entityType);
        ClassName baseRepo = ClassName.get("dev.sweety.sql4j.impl", "BaseRepository");
        ParameterizedTypeName superPath = ParameterizedTypeName.get(baseRepo, entityTypeName);

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(implName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(superPath)
                .addSuperinterface(ClassName.get(typeElement));

        // Constructor
        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterizedTypeName.get(ClassName.get("dev.sweety.sql4j.api.obj", "Table"), entityTypeName), "table")
                .addParameter(ClassName.get("dev.sweety.sql4j.api.connection.dialect", "Dialect"), "dialect")
                .addParameter(ClassName.get("dev.sweety.sql4j.impl.query", "QueryCache"), "cache")
                .addParameter(ClassName.get("dev.sweety.sql4j.api.obj.table", "TableRegistry"), "registry")
                .addParameter(ClassName.get("dev.sweety.sql4j.impl.cache", "EntityCache"), "entityCache")
                .addStatement("super(table, dialect, cache, registry, entityCache)")
                .build();
        classBuilder.addMethod(constructor);

        // Methods
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) enclosed;
                AnnotationMirror queryAnn = getAnnotation(method, QUERY_ANNOTATION);
                if (queryAnn != null) {
                    String sql = getAnnotationValue(queryAnn, "value");
                    if (sql.startsWith("\"") && sql.endsWith("\"")) sql = sql.substring(1, sql.length() - 1);

                    // Build params list
                    StringBuilder paramsList = new StringBuilder("java.util.List.of(");
                    for (int i = 0; i < method.getParameters().size(); i++) {
                        paramsList.append(method.getParameters().get(i).getSimpleName());
                        if (i < method.getParameters().size() - 1) paramsList.append(", ");
                    }
                    paramsList.append(")");

                    ClassName paramQuery = ClassName.get("dev.sweety.sql4j.api.query", "ParamQuery");

                    // Check for @CacheEvict
                    AnnotationMirror evictAnn = getAnnotation(method, CACHE_EVICT_ANNOTATION);
                    MethodSpec.Builder mb = MethodSpec.overriding(method);

                    if (evictAnn != null) {
                        String scopeStr = getAnnotationValue(evictAnn, "scope");
                        boolean byId = scopeStr != null && scopeStr.contains("BY_ID");

                        if (byId) {
                            // Find the id/pk parameter
                            String idParam = method.getParameters().stream()
                                    .map(p -> p.getSimpleName().toString())
                                    .filter(n -> n.equals("id") || n.equals("pk") || n.endsWith("Id") || n.endsWith("PK"))
                                    .findFirst()
                                    .orElse(method.getParameters().isEmpty() ? null
                                            : method.getParameters().get(0).getSimpleName().toString());

                            if (idParam == null) {
                                processingEnv.getMessager().printMessage(
                                        javax.tools.Diagnostic.Kind.ERROR,
                                        "@CacheEvict(scope=BY_ID) requires a parameter named 'id' or 'pk'",
                                        method);
                                continue;
                            }
                            mb.addStatement(
                                    "return new $T<>($S, $L, (dev.sweety.sql4j.api.obj.Table) table())"
                                    + ".withPostAction(__ -> entityCache.evict(table().clazz(), $L))",
                                    paramQuery, sql, paramsList, idParam);
                        } else {
                            mb.addStatement(
                                    "return new $T<>($S, $L, (dev.sweety.sql4j.api.obj.Table) table())"
                                    + ".withPostAction(__ -> entityCache.evictAll(table().clazz()))",
                                    paramQuery, sql, paramsList);
                        }
                    } else {
                        mb.addStatement("return new $T<>($S, $L, (dev.sweety.sql4j.api.obj.Table) table())",
                                paramQuery, sql, paramsList.toString());
                    }

                    classBuilder.addMethod(mb.build());
                }
            }
        }

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build()).build();
        javaFile.writeTo(processingEnv.getFiler());
    }

    private void parseIndexAnnotation(AnnotationMirror am, String tableName, List<IndexData> target) {
        String name = null;
        List<String> cols = new ArrayList<>();
        boolean unique = false;

        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : am.getElementValues().entrySet()) {
            String key = entry.getKey().getSimpleName().toString();
            if (key.equals("name")) {
                name = entry.getValue().getValue().toString();
            } else if (key.equals("unique")) {
                unique = "true".equals(entry.getValue().getValue().toString());
            } else if (key.equals("columns")) {
                if (entry.getValue().getValue() instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof AnnotationValue av) {
                            String c = av.getValue().toString().trim();
                            if (!c.isEmpty()) cols.add(c);
                        }
                    }
                }
            }
        }

        if (!cols.isEmpty()) {
            if (name == null || name.isEmpty()) {
                name = "idx_" + tableName + "_" + String.join("_", cols);
            }
            target.add(new IndexData(name, cols, unique));
        }
    }

    private record IndexData(String name, List<String> columns, boolean unique) {
    }

    private record FieldData(String fieldName, String colName, TypeName fieldType, TypeName boxedType, TypeName columnType,
                             boolean isPrivate, boolean isPrimaryKey, boolean isAutoInc, boolean isNullable, boolean isSoftDelete,
                             String indexName, boolean isUniqueIndex) {
    }

    private record RelationData(String constName, String type, String fieldName, TypeName targetClass, String mappedBy,
                                String colRef, String fetchType) {
    }
}
