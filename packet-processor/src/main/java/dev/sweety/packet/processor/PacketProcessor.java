package dev.sweety.packet.processor;


import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import dev.sweety.netty.packet.buffer.PacketBuffer;
import dev.sweety.netty.packet.model.Packet;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
                messager.printMessage(Diagnostic.Kind.ERROR, "Can only be applied to interfaces", element);
                return true;
            }

            TypeElement typeElement = (TypeElement) element;
            try {
                generatePacketClass(typeElement);
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Couldn't generate packet class", element);
            }
        }
        return true;
    }

    private void generatePacketClass(TypeElement interfaceElement) throws IOException {
        String interfaceName = interfaceElement.getSimpleName().toString();
        String packetName = interfaceName + "Packet";
        String packetPackage = elementUtils.getPackageOf(interfaceElement).getQualifiedName().toString();

        ClassName packetClassName = ClassName.get(packetPackage, interfaceName);

        List<FieldSpec> fields = new ArrayList<>();
        MethodSpec.Builder writeConstructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);

        MethodSpec.Builder readConstructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(short.class, "_id")
                .addParameter(long.class, "_timestamp")
                .addParameter(byte[].class, "_data")
                .addStatement("super(_id, _timestamp, _data)");

        List<? extends Element> enclosedElements = interfaceElement.getEnclosedElements();

        for (Element enclosedElement : enclosedElements) {
            if (enclosedElement.getKind() != ElementKind.METHOD) continue;

            ExecutableElement method = (ExecutableElement) enclosedElement;
            String fieldName = method.getSimpleName().toString();
            TypeMirror returnType = method.getReturnType();
            TypeName returnTypeName = TypeName.get(returnType);

            FieldSpec field = FieldSpec.builder(returnTypeName, fieldName, Modifier.PRIVATE).build();
            fields.add(field);

            writeConstructorBuilder.addParameter(returnTypeName, fieldName);

            FieldBuffer fieldBuffer = method.getAnnotation(FieldBuffer.class);

            if (fieldBuffer != null) {
                TypeName encoderType = getEncoderTypeName(fieldBuffer);
                TypeName decoderType = getDecoderTypeName(fieldBuffer);
                writeConstructorBuilder.addStatement("this.buffer().writeObject($N, $T.encoder($T.class))", fieldName, TypeName.get(BufferUtils.class), encoderType);
                readConstructorBuilder.addStatement("this.$N = this.buffer().readObject($T.decoder($T.class))", fieldName, TypeName.get(BufferUtils.class), decoderType);
                continue;
            }

            generateBuffer(fieldName, returnType, writeConstructorBuilder, readConstructorBuilder, method);
        }

        TypeSpec.Builder eventClassBuilder = TypeSpec.classBuilder(packetName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(ClassName.get(Packet.class))
                .addSuperinterface(packetClassName)
                .addFields(fields)
                .addMethod(writeConstructorBuilder.build())
                .addMethod(readConstructorBuilder.build());

        // add getter methods implementing the interface
        for (FieldSpec f : fields) {
            MethodSpec getter = MethodSpec.methodBuilder(f.name)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .returns(f.type)
                    .addStatement("return this.$N", f.name)
                    .build();
            eventClassBuilder.addMethod(getter);
        }

        JavaFile javaFile = JavaFile.builder(packetPackage, eventClassBuilder.build())
                .build();

        javaFile.writeTo(processingEnv.getFiler());
    }

    private void generateBuffer(String fieldName, TypeMirror returnType, MethodSpec.Builder writeConstructorBuilder, MethodSpec.Builder readConstructorBuilder, ExecutableElement method) {
        List<String> unsupported = new ArrayList<>();
        TypeKind kind = returnType.getKind();
        switch (kind) {
            case BOOLEAN, FLOAT, CHAR, LONG, SHORT, BYTE, DOUBLE -> {
                String name = kind.name().toLowerCase();
                name = name.substring(0, 1).toUpperCase() + name.substring(1);
                writeConstructorBuilder.addStatement("this.buffer().write$N($N)", name, fieldName);
                readConstructorBuilder.addStatement("this.$N = this.buffer().read$N()", fieldName, name);
            }
            case INT -> {
                writeConstructorBuilder.addStatement("this.buffer().writeVarInt($N)", fieldName);
                readConstructorBuilder.addStatement("this.$N = this.buffer().readVarInt()", fieldName);
            }
            case DECLARED -> {
                String typeString = returnType.toString();
                if (typeString.equals("java.lang.String")) {
                    writeConstructorBuilder.addStatement("this.buffer().writeString($N)", fieldName);
                    readConstructorBuilder.addStatement("this.$N = this.buffer().readString()", fieldName);
                } else if (typeString.equals("java.util.UUID")) {
                    writeConstructorBuilder.addStatement("this.buffer().writeUuid($N)", fieldName);
                    readConstructorBuilder.addStatement("this.$N = this.buffer().readUuid()", fieldName);
                } else {
                    TypeName typeName = TypeName.get(returnType);
                    if (typeName.isBoxedPrimitive()) {
                        TypeName unboxed = typeName.unbox();
                        String primitiveName = unboxed.toString();

                        switch (primitiveName) {
                            case "int" -> {
                                writeConstructorBuilder.addStatement("this.buffer().writeVarInt($N)", fieldName);
                                readConstructorBuilder.addStatement("this.$N = this.buffer().readVarInt()", fieldName);
                            }
                            case "boolean", "float", "char", "long", "short", "byte", "double" -> {
                                String capitalized = primitiveName.substring(0, 1).toUpperCase() + primitiveName.substring(1);
                                writeConstructorBuilder.addStatement("this.buffer().write$N($N)", capitalized, fieldName);
                                readConstructorBuilder.addStatement("this.$N = this.buffer().read$N()", fieldName, capitalized);
                            }
                            default -> unsupported.add("Unsupported boxed primitive type: " + typeString);
                        }
                    } else {

                        if (typeUtils.asElement(returnType).getKind() == ElementKind.ENUM) {
                            writeConstructorBuilder.addStatement("this.buffer().writeEnum($N)", fieldName);
                            readConstructorBuilder.addStatement("this.$N = this.buffer().readEnum($T.class)", fieldName, TypeName.get(returnType));
                        } else unsupported.add("Unsupported declared type: " + typeString);
                    }
                }
            }

            case ARRAY -> {
                ArrayType arrayType = (ArrayType) returnType;
                TypeMirror componentType = arrayType.getComponentType();
                TypeKind componentKind = componentType.getKind();

                switch (componentKind) {
                    case BOOLEAN, FLOAT, CHAR, LONG, SHORT, BYTE, DOUBLE -> {
                        String name = componentKind.name().toLowerCase();
                        name = name.substring(0, 1).toUpperCase() + name.substring(1);
                        writeConstructorBuilder.addStatement("this.buffer().write$NArray($N)", name, fieldName);
                        readConstructorBuilder.addStatement("this.$N = this.buffer().read$NArray()", fieldName, name);
                    }
                    case INT -> {
                        writeConstructorBuilder.addStatement("this.buffer().writeVarIntArray($N)", fieldName);
                        readConstructorBuilder.addStatement("this.$N = this.buffer().readVarIntArray()", fieldName);
                    }
                    case DECLARED -> {
                        String typeString = componentType.toString();
                        if (typeString.equals("java.lang.String")) {

                            writeConstructorBuilder.addStatement("this.buffer().writeArray($T::writeString,$N)", TypeName.get(PacketBuffer.class), fieldName);
                            readConstructorBuilder.addStatement("this.$N = this.buffer().readArray($T::readString, String[]::new)", fieldName, TypeName.get(PacketBuffer.class));
                        } else if (typeString.equals("java.util.UUID")) {
                            writeConstructorBuilder.addStatement("this.buffer().writeArray($T::writeUuid,$N)", TypeName.get(PacketBuffer.class), fieldName);
                            readConstructorBuilder.addStatement("this.$N = this.buffer().readArray($T::readUuid, UUID[]::new)", fieldName, TypeName.get(PacketBuffer.class));
                        } else {
                            TypeName typeName = TypeName.get(componentType);
                            if (typeName.isBoxedPrimitive()) {
                                TypeName unboxed = typeName.unbox();
                                String primitiveName = unboxed.toString();

                                switch (primitiveName) {
                                    case "int", "boolean", "float", "char", "long", "short", "byte", "double" -> {
                                        String capitalized = primitiveName.substring(0, 1).toUpperCase() + primitiveName.substring(1);
                                        writeConstructorBuilder.addStatement("this.buffer().writeArray($T::write$N,$N)", TypeName.get(PacketBuffer.class), capitalized, fieldName);
                                        readConstructorBuilder.addStatement("this.$N = this.buffer().readArray($T::read$N, $T[]::new)", fieldName, TypeName.get(PacketBuffer.class), capitalized, typeName);
                                    }
                                    default -> unsupported.add("Unsupported boxed primitive type: " + typeString);
                                }
                            } else {
                                unsupported.add("Unsupported declared array component  type: " + typeString);
                            }
                        }

                    }
                    default -> {
                        unsupported.add("Unsupported return type: " + componentType + " -> " + componentKind);
                    }
                }
            }
            case WILDCARD -> {
                unsupported.add("Unsupported wildcard type: " + returnType);
            }
            default -> {
                unsupported.add("Unsupported return type: " + returnType + " -> " + kind);
            }
        }

        if (!unsupported.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String s : unsupported) {
                sb.append(s).append("\n");
            }
            messager.printMessage(Diagnostic.Kind.ERROR, "Unsupported types in " + method.getSimpleName() + ":\n" + sb, method);
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
}
