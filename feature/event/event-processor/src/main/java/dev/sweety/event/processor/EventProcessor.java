package dev.sweety.event.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import dev.sweety.event.api.AbstractEvent;
import dev.sweety.event.api.Event;
import dev.sweety.event.api.MutableEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("dev.sweety.event.processor.GenerateEvent")
@SupportedSourceVersion(SourceVersion.RELEASE_24)
@AutoService(Processor.class)
public class EventProcessor extends AbstractProcessor {

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
        for (Element element : roundEnv.getElementsAnnotatedWith(GenerateEvent.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Can only be applied to class", element);
                return true;
            }

            TypeElement typeElement = (TypeElement) element;
            try {
                generateEventStructure(typeElement);
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Couldn't generate event structure: " + e.getMessage(), element);
            }
        }
        return true;
    }

    private void generateEventStructure(TypeElement packetElement) throws IOException {
        String baseName = packetElement.getSimpleName().toString();
        if (baseName.endsWith("Packet")) {
            baseName = baseName.substring(0, baseName.length() - 6);
        }
        
        String eventInterfaceName = baseName + "Event";
        String mutableInterfaceName = "Mutable" + eventInterfaceName;
        String immutableImplName = eventInterfaceName + "Impl";
        String mutableImplName = "Mutable" + eventInterfaceName + "Impl";
        
        String packetPackage = elementUtils.getPackageOf(packetElement).getQualifiedName().toString();
        String eventPackage = packetPackage + ".event";

        ClassName packetClassName = ClassName.get(packetPackage, packetElement.getSimpleName().toString());
        ClassName eventInterface = ClassName.get(eventPackage, eventInterfaceName);
        ClassName mutableInterface = ClassName.get(eventPackage, mutableInterfaceName);
        ClassName immutableImpl = ClassName.get(eventPackage, immutableImplName);

        List<FieldInfo> fields = extractFields(packetElement);

        // 1. Generate Read-Only Interface
        TypeSpec.Builder readOnlyInterfaceBuilder = TypeSpec.interfaceBuilder(eventInterfaceName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(Event.class);
        
        for (FieldInfo field : fields) {
            readOnlyInterfaceBuilder.addMethod(MethodSpec.methodBuilder(field.getterName)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(field.type)
                    .build());
        }
        writeJavaFile(eventPackage, readOnlyInterfaceBuilder.build());

        // 2. Generate Mutable Interface
        TypeSpec.Builder mutableInterfaceBuilder = TypeSpec.interfaceBuilder(mutableInterfaceName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(eventInterface)
                .addSuperinterface(MutableEvent.class);
        
        for (FieldInfo field : fields) {
            mutableInterfaceBuilder.addMethod(MethodSpec.methodBuilder(field.setterName)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .addParameter(field.type, field.name)
                    .build());
        }
        writeJavaFile(eventPackage, mutableInterfaceBuilder.build());

        // 3. Generate Immutable Implementation Class (XEventImpl)
        TypeSpec.Builder immutableClassBuilder = TypeSpec.classBuilder(immutableImplName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(eventInterface)
                .addAnnotation(Data.class);

        immutableClassBuilder.addField(FieldSpec.builder(packetClassName, "packet", Modifier.PRIVATE, Modifier.FINAL).build());
        
        // IEvent fields for the immutable version (since it's a snapshot/view)
        immutableClassBuilder.addField(FieldSpec.builder(TypeName.BOOLEAN, "post", Modifier.PRIVATE, Modifier.FINAL).build());
        immutableClassBuilder.addField(FieldSpec.builder(TypeName.BOOLEAN, "pre", Modifier.PRIVATE, Modifier.FINAL).build());
        immutableClassBuilder.addField(FieldSpec.builder(TypeName.BOOLEAN, "cancelled", Modifier.PRIVATE, Modifier.FINAL).build());
        immutableClassBuilder.addField(FieldSpec.builder(TypeName.BOOLEAN, "changed", Modifier.PRIVATE, Modifier.FINAL).build());

        for (FieldInfo field : fields) {
            immutableClassBuilder.addField(FieldSpec.builder(field.type, field.name, Modifier.PRIVATE, Modifier.FINAL).build());
        }

        MethodSpec.Builder immutableConstructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(eventInterface, "other");
        
        immutableConstructorBuilder.addStatement("this.packet = (($T)other).getPacket()", ClassName.get(eventPackage, mutableImplName));
        immutableConstructorBuilder.addStatement("this.post = other.isPost()");
        immutableConstructorBuilder.addStatement("this.pre = other.isPre()");
        immutableConstructorBuilder.addStatement("this.cancelled = other.isCancelled()");
        immutableConstructorBuilder.addStatement("this.changed = other.isChanged()");
        
        for (FieldInfo field : fields) {
            immutableConstructorBuilder.addStatement("this.$N = other.$N()", field.name, field.getterName);
        }
        immutableClassBuilder.addMethod(immutableConstructorBuilder.build());

        // Implement Event methods
        immutableClassBuilder.addMethod(MethodSpec.methodBuilder("isPost").addModifiers(Modifier.PUBLIC).addAnnotation(Override.class).returns(TypeName.BOOLEAN).addStatement("return post").build());
        immutableClassBuilder.addMethod(MethodSpec.methodBuilder("isPre").addModifiers(Modifier.PUBLIC).addAnnotation(Override.class).returns(TypeName.BOOLEAN).addStatement("return pre").build());
        immutableClassBuilder.addMethod(MethodSpec.methodBuilder("isCancelled").addModifiers(Modifier.PUBLIC).addAnnotation(Override.class).returns(TypeName.BOOLEAN).addStatement("return cancelled").build());
        immutableClassBuilder.addMethod(MethodSpec.methodBuilder("isChanged").addModifiers(Modifier.PUBLIC).addAnnotation(Override.class).returns(TypeName.BOOLEAN).addStatement("return changed").build());
        immutableClassBuilder.addMethod(MethodSpec.methodBuilder("post").addModifiers(Modifier.PUBLIC).addAnnotation(Override.class).addAnnotation(NotNull.class).returns(Event.class).addStatement("return this").build());

        writeJavaFile(eventPackage, immutableClassBuilder.build());

        // 4. Generate Mutable Implementation Class (MutableXEventImpl)
        TypeSpec.Builder mutableImplClassBuilder = TypeSpec.classBuilder(mutableImplName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(AbstractEvent.class)
                .addSuperinterface(mutableInterface)
                .addAnnotation(Data.class)
                .addAnnotation(AnnotationSpec.builder(EqualsAndHashCode.class).addMember("callSuper", "true").build());

        mutableImplClassBuilder.addField(FieldSpec.builder(packetClassName, "packet", Modifier.PRIVATE, Modifier.FINAL).build());
        
        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(packetClassName, "packet")
                .addStatement("this.packet = packet");

        for (FieldInfo field : fields) {
            mutableImplClassBuilder.addField(FieldSpec.builder(field.type, field.name, Modifier.PRIVATE).build());
            constructorBuilder.addStatement("this.$N = packet.$N()", field.name, field.getterName);
        }
        mutableImplClassBuilder.addMethod(constructorBuilder.build());
        
        mutableImplClassBuilder.addMethod(MethodSpec.methodBuilder("getPacket").addModifiers(Modifier.PUBLIC).returns(packetClassName).addStatement("return packet").build());

        mutableImplClassBuilder.addMethod(MethodSpec.methodBuilder("toImmutable")
                .addModifiers(Modifier.PUBLIC)
                .returns(eventInterface)
                .addStatement("return new $T(this)", immutableImpl)
                .build());

        writeJavaFile(eventPackage, mutableImplClassBuilder.build());
    }

    private List<FieldInfo> extractFields(TypeElement element) {
        List<FieldInfo> fields = new ArrayList<>();
        List<? extends Element> enclosedElements = element.getEnclosedElements();

        for (Element enclosed : enclosedElements) {
            if (enclosed.getKind() != ElementKind.FIELD) continue;

            VariableElement variable = (VariableElement) enclosed;
            String name = variable.getSimpleName().toString();
            if (name.startsWith("_")) continue;

            String getterName = (variable.asType().toString().equalsIgnoreCase("boolean") ? "is" : "get") + capitalize(name);
            String setterName = "set" + capitalize(name);

            fields.add(new FieldInfo(name, TypeName.get(variable.asType()), getterName, setterName));
        }
        return fields;
    }

    private void writeJavaFile(String packageName, TypeSpec typeSpec) throws IOException {
        JavaFile.builder(packageName, typeSpec).build().writeTo(processingEnv.getFiler());
    }

    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static class FieldInfo {
        final String name;
        final TypeName type;
        final String getterName;
        final String setterName;

        FieldInfo(String name, TypeName type, String getterName, String setterName) {
            this.name = name;
            this.type = type;
            this.getterName = getterName;
            this.setterName = setterName;
        }
    }
}
