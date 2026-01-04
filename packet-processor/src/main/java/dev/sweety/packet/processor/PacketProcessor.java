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
        final String interfaceName = interfaceElement.getSimpleName().toString();
        final String packetName = interfaceName + "Packet";
        final String packetPackage = elementUtils.getPackageOf(interfaceElement).getQualifiedName().toString();

        final ClassName packetClassName = ClassName.get(packetPackage, interfaceName);

        final List<FieldSpec> fields = new ArrayList<>();
        final MethodSpec.Builder writeConstructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);

        final MethodSpec.Builder readConstructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(short.class, "_id")
                .addParameter(long.class, "_timestamp")
                .addParameter(byte[].class, "_data")
                .addStatement("super(_id, _timestamp, _data)");

        final List<? extends Element> enclosedElements = interfaceElement.getEnclosedElements();

        for (Element enclosedElement : enclosedElements) {
            if (enclosedElement.getKind() != ElementKind.METHOD) continue;

            final ExecutableElement method = (ExecutableElement) enclosedElement;
            final String fieldName = method.getSimpleName().toString();
            final TypeMirror returnType = method.getReturnType();
            final TypeName returnTypeName = TypeName.get(returnType);

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
            case BOOLEAN, FLOAT, SHORT, BYTE, DOUBLE, CHAR -> {
                String name = capitalize(kind.name().toLowerCase());
                writeConstructorBuilder.addStatement("this.buffer().write$N($N)", name, fieldName);
                readConstructorBuilder.addStatement("this.$N = this.buffer().read$N()", fieldName, name);
            }

            case INT, LONG -> {
                String name = capitalize(kind.name().toLowerCase());
                writeConstructorBuilder.addStatement("this.buffer().writeVar$N($N)", name, fieldName);
                readConstructorBuilder.addStatement("this.$N = this.buffer().readVar$N()", fieldName, name);
            }
            case DECLARED -> {
                String typeString = returnType.toString();
                if (typeString.equals("java.lang.String")) {
                    writeConstructorBuilder.addStatement("this.buffer().writeString($N)", fieldName);
                    readConstructorBuilder.addStatement("this.$N = this.buffer().readString()", fieldName);
                } else if (typeString.equals("java.util.UUID")) {
                    writeConstructorBuilder.addStatement("this.buffer().writeUuid($N)", fieldName);
                    readConstructorBuilder.addStatement("this.$N = this.buffer().readUuid()", fieldName);
                } else if (typeUtils.asElement(returnType).getKind() == ElementKind.ENUM) {
                    writeConstructorBuilder.addStatement("this.buffer().writeEnum($N)", fieldName);
                    readConstructorBuilder.addStatement("this.$N = this.buffer().readEnum($T.class)", fieldName, TypeName.get(returnType));
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
                            case "char" -> {
                                writeConstructorBuilder.addStatement("this.buffer().writeString(new $T(new char[]{$N}))", TypeName.get(String.class), fieldName);
                                readConstructorBuilder.addStatement("this.$N = this.buffer().readString().toCharArray()[0]", fieldName);
                            }
                            case "boolean", "float", "long", "short", "byte", "double" -> {
                                String capitalized = capitalize(primitiveName);
                                writeConstructorBuilder.addStatement("this.buffer().write$N($N)", capitalized, fieldName);
                                readConstructorBuilder.addStatement("this.$N = this.buffer().read$N()", fieldName, capitalized);
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
                        writeConstructorBuilder.addStatement("this.buffer().write$NArray($N)", name, fieldName);
                        readConstructorBuilder.addStatement("this.$N = this.buffer().read$NArray()", fieldName, name);
                    }
                    case INT, LONG -> {
                        String name = capitalize(componentKind.name().toLowerCase());
                        writeConstructorBuilder.addStatement("this.buffer().writeVar$NArray($N)", name, fieldName);
                        readConstructorBuilder.addStatement("this.$N = this.buffer().readVar$NArray()", fieldName, name);
                    }

                    case DECLARED -> {
                        String typeString = componentType.toString();
                        if (typeString.equals("java.lang.String")) {
                            writeConstructorBuilder.addStatement("this.buffer().writeArray($T::writeString,$N)", TypeName.get(PacketBuffer.class), fieldName);
                            readConstructorBuilder.addStatement("this.$N = this.buffer().readArray($T::readString, $T[]::new)", fieldName, TypeName.get(PacketBuffer.class), TypeName.get(String.class));
                        } else if (typeString.equals("java.util.UUID")) {
                            writeConstructorBuilder.addStatement("this.buffer().writeArray($T::writeUuid,$N)", TypeName.get(PacketBuffer.class), fieldName);
                            readConstructorBuilder.addStatement("this.$N = this.buffer().readArray($T::readUuid, $T[]::new)", fieldName, TypeName.get(PacketBuffer.class), TypeName.get(UUID.class));
                        } else if (typeUtils.asElement(componentType).getKind() == ElementKind.ENUM) {
                            writeConstructorBuilder.addStatement("this.buffer().writeArray($T::writeEnum,$N)", TypeName.get(PacketBuffer.class), fieldName);
                            // Usa una lambda per passare la classe dell'enum al decoder
                            readConstructorBuilder.addStatement("this.$N = this.buffer().readArray(buffer -> buffer.readEnum($T.class), $T[]::new)", fieldName, TypeName.get(componentType), TypeName.get(componentType));
                        } else {
                            TypeName typeName = TypeName.get(componentType);
                            if (typeName.isBoxedPrimitive()) {
                                TypeName unboxed = typeName.unbox();
                                String primitiveName = unboxed.toString();

                                switch (primitiveName) {
                                    case "int", "boolean", "float", "long", "short", "byte", "double" -> {
                                        String capitalized = capitalize(primitiveName);
                                        writeConstructorBuilder.addStatement("this.buffer().writeArray($T::write$N,$N)", TypeName.get(PacketBuffer.class), capitalized, fieldName);
                                        readConstructorBuilder.addStatement("this.$N = this.buffer().readArray($T::read$N, $T[]::new)", fieldName, TypeName.get(PacketBuffer.class), capitalized, typeName);
                                    }

                                    case "char" -> {
                                        writeConstructorBuilder.addStatement("this.buffer().writeString(new $T(new char[]{$N}))", TypeName.get(String.class), fieldName);
                                        readConstructorBuilder.addStatement("this.$N = this.buffer().readString().toCharArray()", fieldName);
                                    }

                                    default -> unsupported.add("Unsupported boxed primitive type: " + typeString);
                                }
                            } else {
                                unsupported.add("Unsupported declared array component  type: " + typeString);
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

    // Helper per capitalizzare il nome di un tipo primitivo (es. "int" -> "Int")
    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        char first = name.charAt(0);
        if (Character.isUpperCase(first)) return name;
        return Character.toUpperCase(first) + name.substring(1);
    }
}
