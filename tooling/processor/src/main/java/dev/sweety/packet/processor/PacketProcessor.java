package dev.sweety.packet.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dev.sweety.data.buffer.BufferReader;
import dev.sweety.data.buffer.BufferWriter;
import dev.sweety.netty.packet.model.Packet;
import org.jetbrains.annotations.NotNull;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@SupportedAnnotationTypes("dev.sweety.packet.processor.BuildPacket")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@AutoService(Processor.class)
public class PacketProcessor extends AbstractProcessor {

    private Messager messager;
    private Elements elementUtils;
    private Types typeUtils;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        elementUtils = processingEnv.getElementUtils();
        typeUtils = processingEnv.getTypeUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(BuildPacket.class)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                if (element.getKind() != ElementKind.METHOD)
                    messager.printMessage(Diagnostic.Kind.ERROR, "Can only be applied to interfaces", element);
                return true;
            }

            final TypeElement typeElement = (TypeElement) element;
            try {
                generatePacketClass(typeElement);
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Couldn't generate packet class", element);
            }
        }
        return true;
    }

    private void generatePacketClass(TypeElement interfaceElement) throws IOException {
        BuildPacket buildPacket = interfaceElement.getAnnotation(BuildPacket.class);

        final String interfaceName = interfaceElement.getSimpleName().toString();
        final String packetPackage = elementUtils.getPackageOf(interfaceElement).getQualifiedName().toString();
        final String packetName = (buildPacket == null || buildPacket.name() == null || buildPacket.name().isEmpty()) ? (interfaceName + "Packet") : buildPacket.name();
        final String packetBuildPackage = packetPackage + ((buildPacket == null || buildPacket.path() == null) ? ("") : buildPacket.path());
        final ClassName packetClassName = ClassName.get(packetPackage, interfaceName);

        final ArrayList<AnnotationSpec> annotations = getAnnotations(buildPacket);

        // No-arg ctor — used by RegisteredPacket for decode
        final MethodSpec noArgCtor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .build();

        // Encode ctor — stores fields, no buffer I/O
        final MethodSpec.Builder encodeCtorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);

        // write(BufferWriter buffer) — serializes fields
        final MethodSpec.Builder writeMethodBuilder = MethodSpec.methodBuilder("write")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(ParameterSpec.builder(TypeName.get(BufferWriter.class), "buffer", Modifier.FINAL).build())
                .returns(TypeName.VOID);

        // read(BufferReader buffer) — deserializes fields
        final MethodSpec.Builder readMethodBuilder = MethodSpec.methodBuilder("read")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(ParameterSpec.builder(TypeName.get(BufferReader.class), "buffer", Modifier.FINAL).build())
                .returns(TypeName.VOID);

        final List<? extends Element> enclosedElements = interfaceElement.getEnclosedElements();

        TypeSpec.Builder eventClassBuilder = TypeSpec.classBuilder(packetName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(ClassName.get(Packet.class))
                .addSuperinterface(packetClassName);

        for (Element enclosedElement : enclosedElements) {
            if (enclosedElement.getKind() != ElementKind.METHOD) continue;
            BuildPacket fieldBuildPacket = enclosedElement.getAnnotation(BuildPacket.class);

            final ExecutableElement method = (ExecutableElement) enclosedElement;
            final String originalFieldName = method.getSimpleName().toString();

            final String fieldName;
            final boolean replace;
            if (fieldBuildPacket == null || fieldBuildPacket.name() == null || fieldBuildPacket.name().isEmpty()) {
                fieldName = originalFieldName;
                replace = false;
            } else {
                fieldName = fieldBuildPacket.name();
                replace = fieldBuildPacket.addMethod();
            }

            final TypeMirror returnType = method.getReturnType();
            final TypeName returnTypeName = TypeName.get(returnType);

            ArrayList<AnnotationSpec> fieldAnnotations = getAnnotations(fieldBuildPacket);

            FieldSpec.Builder field = FieldSpec.builder(returnTypeName, fieldName, Modifier.PRIVATE);

            if (!fieldAnnotations.isEmpty()) for (AnnotationSpec annotation : fieldAnnotations)
                field.addAnnotation(annotation);

            eventClassBuilder.addField(field.build());
            eventClassBuilder.addMethod(
                    MethodSpec.methodBuilder(originalFieldName)
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(Override.class)
                            .returns(returnTypeName)
                            .addStatement("return this.$N", fieldName)
                            .build()
            );

            if (!originalFieldName.equals(fieldName) && replace)
                eventClassBuilder.addMethod(
                        MethodSpec.methodBuilder(fieldName)
                                .addModifiers(Modifier.PUBLIC)
                                .returns(returnTypeName)
                                .addStatement("return this.$N", fieldName)
                                .build()
                );

            // Encode ctor: store parameter into field
            encodeCtorBuilder.addParameter(returnTypeName, fieldName, Modifier.FINAL);
            encodeCtorBuilder.addStatement("this.$N = $N", fieldName, fieldName);

            FieldBuffer fieldBuffer = method.getAnnotation(FieldBuffer.class);

            if (fieldBuffer != null) {
                TypeName encoderType = getEncoderTypeName(fieldBuffer);
                TypeName decoderType = getDecoderTypeName(fieldBuffer);
                writeMethodBuilder.addStatement("buffer.writeObject($N, $T.encoder($T.class))", fieldName, TypeName.get(BufferUtils.class), encoderType);
                readMethodBuilder.addStatement("this.$N = buffer.readObject($T.decoder($T.class))", fieldName, TypeName.get(BufferUtils.class), decoderType);
                continue;
            }

            generateBuffer(fieldName, returnType, writeMethodBuilder, readMethodBuilder, method);
        }

        eventClassBuilder
                .addMethod(noArgCtor)
                .addMethod(encodeCtorBuilder.build())
                .addMethod(writeMethodBuilder.build())
                .addMethod(readMethodBuilder.build());

        if (!annotations.isEmpty()) for (AnnotationSpec annotation : annotations) {
            eventClassBuilder.addAnnotation(annotation);
        }

        JavaFile javaFile = JavaFile.builder(packetBuildPackage, eventClassBuilder.build())
                .build();

        javaFile.writeTo(processingEnv.getFiler());
    }

    private @NotNull ArrayList<AnnotationSpec> getAnnotations(BuildPacket buildPacket) {

        ArrayList<AnnotationSpec> annotations = new ArrayList<>();

        if (buildPacket != null) {
            try {
                Class<? extends Annotation>[] onType = buildPacket.annotations();
                annotations.ensureCapacity(onType.length);
                for (Class<? extends Annotation> annotation : onType) {
                    annotations.add(AnnotationSpec.builder(ClassName.get(annotation)).build());
                }
            } catch (javax.lang.model.type.MirroredTypesException e) {
                List<? extends TypeMirror> mirrors = e.getTypeMirrors();
                annotations.ensureCapacity(mirrors.size());
                for (TypeMirror tm : mirrors) {
                    Element el = typeUtils.asElement(tm);
                    if (el instanceof TypeElement te) {
                        annotations.add(AnnotationSpec.builder(ClassName.get(te)).build());
                    }
                }
            }
        }
        return annotations;
    }

    private void generateBuffer(String fieldName, TypeMirror returnType,
                                MethodSpec.Builder writeMethodBuilder,
                                MethodSpec.Builder readMethodBuilder,
                                ExecutableElement method) {
        List<String> unsupported = new ArrayList<>();
        TypeKind kind = returnType.getKind();
        switch (kind) {
            case BOOLEAN, FLOAT, SHORT, BYTE, DOUBLE, CHAR -> {
                String name = capitalize(kind.name().toLowerCase());
                writeMethodBuilder.addStatement("buffer.write$N($N)", name, fieldName);
                readMethodBuilder.addStatement("this.$N = buffer.read$N()", fieldName, name);
            }

            case INT, LONG -> {
                String name = capitalize(kind.name().toLowerCase());
                writeMethodBuilder.addStatement("buffer.writeVar$N($N)", name, fieldName);
                readMethodBuilder.addStatement("this.$N = buffer.readVar$N()", fieldName, name);
            }
            case DECLARED -> {
                String typeString = returnType.toString();
                if (typeString.equals("java.lang.String")) {
                    writeMethodBuilder.addStatement("buffer.writeString($N)", fieldName);
                    readMethodBuilder.addStatement("this.$N = buffer.readString()", fieldName);
                } else if (typeString.equals("java.util.UUID")) {
                    writeMethodBuilder.addStatement("buffer.writeUuid($N)", fieldName);
                    readMethodBuilder.addStatement("this.$N = buffer.readUuid()", fieldName);
                } else if (typeUtils.asElement(returnType).getKind() == ElementKind.ENUM) {
                    writeMethodBuilder.addStatement("buffer.writeEnum($N)", fieldName);
                    readMethodBuilder.addStatement("this.$N = buffer.readEnum($T.class)", fieldName, TypeName.get(returnType));
                } else if (hasStaticDecoderField(returnType)) {
                    // Record-style: static DECODER field + write()
                    writeMethodBuilder.addStatement("buffer.writeObject($N)", fieldName);
                    readMethodBuilder.addStatement("this.$N = buffer.readObject($T.DECODER)", fieldName, TypeName.get(returnType));
                } else if (hasNoArgConstructor(returnType)
                        && implementsInterface(returnType, ABSTRACT_ENCODER)
                        && implementsInterface(returnType, ABSTRACT_DECODER)) {
                    // Class-style: no-arg ctor + write() + read()
                    writeMethodBuilder.addStatement("buffer.writeObject($N)", fieldName);
                    readMethodBuilder.addStatement("this.$N = buffer.readObject($T::new)", fieldName, TypeName.get(returnType));
                } else {
                    TypeName typeName = TypeName.get(returnType);
                    if (typeName.isBoxedPrimitive()) {
                        TypeName unboxed = typeName.unbox();
                        String primitiveName = unboxed.toString();

                        switch (primitiveName) {
                            case "int" -> {
                                writeMethodBuilder.addStatement("buffer.writeVarInt($N)", fieldName);
                                readMethodBuilder.addStatement("this.$N = buffer.readVarInt()", fieldName);
                            }
                            case "char" -> {
                                writeMethodBuilder.addStatement("buffer.writeString(new $T(new char[]{$N}))", TypeName.get(String.class), fieldName);
                                readMethodBuilder.addStatement("this.$N = buffer.readString().toCharArray()[0]", fieldName);
                            }
                            case "boolean", "float", "long", "short", "byte", "double" -> {
                                String capitalized = capitalize(primitiveName);
                                writeMethodBuilder.addStatement("buffer.write$N($N)", capitalized, fieldName);
                                readMethodBuilder.addStatement("this.$N = buffer.read$N()", fieldName, capitalized);
                            }
                            default -> unsupported.add("Unsupported boxed primitive type: " + typeString);
                        }
                    } else unsupported.add("Unsupported declared type: " + typeString);
                }
            }

            case ARRAY -> {
                ArrayType arrayType = (ArrayType) returnType;
                TypeMirror componentType = arrayType.getComponentType();
                TypeKind componentKind = componentType.getKind();

                switch (componentKind) {
                    case BOOLEAN, FLOAT, SHORT, BYTE, DOUBLE, CHAR -> {
                        String name = capitalize(componentKind.name().toLowerCase());
                        writeMethodBuilder.addStatement("buffer.write$NArray($N)", name, fieldName);
                        readMethodBuilder.addStatement("this.$N = buffer.read$NArray()", fieldName, name);
                    }
                    case INT, LONG -> {
                        String name = capitalize(componentKind.name().toLowerCase());
                        writeMethodBuilder.addStatement("buffer.writeVar$NArray($N)", name, fieldName);
                        readMethodBuilder.addStatement("this.$N = buffer.readVar$NArray()", fieldName, name);
                    }

                    case DECLARED -> {
                        String typeString = componentType.toString();
                        if (typeString.equals("java.lang.String")) {
                            writeMethodBuilder.addStatement("buffer.writeArray($T::writeString,$N)", TypeName.get(BufferWriter.class), fieldName);
                            readMethodBuilder.addStatement("this.$N = buffer.readArray($T::readString, $T[]::new)", fieldName, TypeName.get(BufferReader.class), TypeName.get(String.class));
                        } else if (typeString.equals("java.util.UUID")) {
                            writeMethodBuilder.addStatement("buffer.writeArray($T::writeUuid,$N)", TypeName.get(BufferWriter.class), fieldName);
                            readMethodBuilder.addStatement("this.$N = buffer.readArray($T::readUuid, $T[]::new)", fieldName, TypeName.get(BufferReader.class), TypeName.get(UUID.class));
                        } else if (typeUtils.asElement(componentType).getKind() == ElementKind.ENUM) {
                            writeMethodBuilder.addStatement("buffer.writeArray($T::writeEnum,$N)", TypeName.get(BufferWriter.class), fieldName);
                            readMethodBuilder.addStatement("this.$N = buffer.readArray(b -> b.readEnum($T.class), $T[]::new)", fieldName, TypeName.get(componentType), TypeName.get(componentType));
                        } else {
                            TypeName typeName = TypeName.get(componentType);
                            if (typeName.isBoxedPrimitive()) {
                                TypeName unboxed = typeName.unbox();
                                String primitiveName = unboxed.toString();

                                switch (primitiveName) {
                                    case "int", "boolean", "float", "long", "short", "byte", "double" -> {
                                        String capitalized = capitalize(primitiveName);
                                        writeMethodBuilder.addStatement("buffer.writeArray($T::write$N,$N)", TypeName.get(BufferWriter.class), capitalized, fieldName);
                                        readMethodBuilder.addStatement("this.$N = buffer.readArray($T::read$N, $T[]::new)", fieldName, TypeName.get(BufferReader.class), capitalized, typeName);
                                    }

                                    case "char" -> {
                                        writeMethodBuilder.addStatement("buffer.writeString(new $T(new char[]{$N}))", TypeName.get(String.class), fieldName);
                                        readMethodBuilder.addStatement("this.$N = buffer.readString().toCharArray()", fieldName);
                                    }

                                    default -> unsupported.add("Unsupported boxed primitive type: " + typeString);
                                }
                            } else if (hasStaticDecoderField(componentType)) {
                                writeMethodBuilder.addStatement("buffer.writeArray((b, t) -> t.write(b), $N)", fieldName);
                                readMethodBuilder.addStatement("this.$N = buffer.readArray($T.DECODER, $T[]::new)", fieldName, TypeName.get(componentType), TypeName.get(componentType));
                            } else if (hasNoArgConstructor(componentType)
                                    && implementsInterface(componentType, ABSTRACT_ENCODER)
                                    && implementsInterface(componentType, ABSTRACT_DECODER)) {
                                writeMethodBuilder.addStatement("buffer.writeArray((b, t) -> t.write(b), $N)", fieldName);
                                readMethodBuilder.addStatement("this.$N = buffer.readArray(buf -> {\n$T t = new $T();\nt.read(buf);\nreturn t;\n}, $T[]::new)", fieldName, TypeName.get(componentType), TypeName.get(componentType), TypeName.get(componentType));
                            } else {
                                unsupported.add("Unsupported declared array component type: " + typeString);
                            }
                        }

                    }
                    default -> unsupported.add("Unsupported return type: " + componentType + " -> " + componentKind);
                }
            }
            case WILDCARD -> unsupported.add("Unsupported wildcard type: " + returnType);
            default -> unsupported.add("Unsupported return type: " + returnType + " -> " + kind);
        }

        if (!unsupported.isEmpty()) {
            String msg = String.join("\n", unsupported);
            messager.printMessage(Diagnostic.Kind.ERROR, "Unsupported types in " + method.getSimpleName() + ":\n" + msg, method);
        }
    }

    private TypeName getEncoderTypeName(FieldBuffer fieldBuffer) {
        try {
            return TypeName.get(fieldBuffer.encoder());
        } catch (javax.lang.model.type.MirroredTypeException e) {
            TypeMirror tm = e.getTypeMirror();
            return TypeName.get(tm);
        }
    }

    private TypeName getDecoderTypeName(FieldBuffer fieldBuffer) {
        try {
            return TypeName.get(fieldBuffer.decoder());
        } catch (javax.lang.model.type.MirroredTypeException e) {
            TypeMirror tm = e.getTypeMirror();
            return TypeName.get(tm);
        }
    }

    private static final String ABSTRACT_ENCODER = "dev.sweety.data.buffer.io.AbstractEncoder";
    private static final String ABSTRACT_DECODER = "dev.sweety.data.buffer.io.AbstractDecoder";

    private boolean implementsInterface(TypeMirror type, String ifaceFqn) {
        Element el = typeUtils.asElement(type);
        if (!(el instanceof TypeElement te)) return false;
        for (TypeMirror iface : te.getInterfaces()) {
            Element ifaceEl = typeUtils.asElement(iface);
            if (ifaceEl instanceof TypeElement ite && ifaceFqn.equals(ite.getQualifiedName().toString()))
                return true;
        }
        return false;
    }

    private boolean hasNoArgConstructor(TypeMirror type) {
        Element el = typeUtils.asElement(type);
        if (!(el instanceof TypeElement te)) return false;
        for (Element enclosed : te.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement ctor = (ExecutableElement) enclosed;
                if (ctor.getParameters().isEmpty()
                        && ctor.getModifiers().contains(Modifier.PUBLIC)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasStaticDecoderField(TypeMirror type) {
        Element el = typeUtils.asElement(type);
        if (!(el instanceof TypeElement te)) return false;
        for (Element enclosed : te.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD
                    && enclosed.getSimpleName().contentEquals("DECODER")
                    && enclosed.getModifiers().contains(Modifier.PUBLIC)
                    && enclosed.getModifiers().contains(Modifier.STATIC)) {
                return true;
            }
        }
        return false;
    }

    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        char first = name.charAt(0);
        if (Character.isUpperCase(first)) return name;
        return Character.toUpperCase(first) + name.substring(1);
    }
}
