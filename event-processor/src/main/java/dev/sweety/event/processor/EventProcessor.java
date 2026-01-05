package dev.sweety.event.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import dev.sweety.event.Event;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

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
@SupportedSourceVersion(SourceVersion.RELEASE_21)
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
                generateEventClass(typeElement);
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Couldn't generate event class", element);
            }
        }
        return true;
    }

    private void generateEventClass(TypeElement packetElement) throws IOException {
        String packetName = packetElement.getSimpleName().toString();
        String eventName = packetName + "Event";
        String packetPackage = elementUtils.getPackageOf(packetElement).getQualifiedName().toString();
        String eventPackage = packetPackage + ".event";

        ClassName packetClassName = ClassName.get(packetPackage, packetName);

        List<FieldSpec> fields = new ArrayList<>();
        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(packetClassName, "p");

        List<? extends Element> enclosedElements = packetElement.getEnclosedElements();

        for (Element enclosedElement : enclosedElements) {
            if (enclosedElement.getKind() != ElementKind.FIELD) continue;

            VariableElement variableElement = (VariableElement) enclosedElement;
            String fieldName = variableElement.getSimpleName().toString();


            String getterName = "get" + capitalize(fieldName);
            boolean hasGetter = variableElement.getAnnotation(Getter.class) != null;

            TypeMirror fieldType = variableElement.asType();

            if (hasGetter && fieldType.toString().equalsIgnoreCase("boolean")){
                getterName = "is" + capitalize(fieldName);
            }

            for (int i = 0; i < enclosedElements.size() && !hasGetter; i++) {
                Element member = enclosedElements.get(i);
                String methodName = member.getSimpleName().toString();
                if (member.getKind() == ElementKind.METHOD &&
                        (methodName.equalsIgnoreCase(getterName) || methodName.equalsIgnoreCase("is" + fieldName) || methodName.equalsIgnoreCase("has" + fieldName) || methodName.equalsIgnoreCase(fieldName))
                ) {
                    ExecutableElement method = (ExecutableElement) member;
                    if (method.getParameters().isEmpty()) {
                        hasGetter = true;
                        getterName = methodName;
                    }
                }
            }


            if (hasGetter) {
                fields.add(FieldSpec.builder(TypeName.get(fieldType), fieldName, Modifier.PRIVATE, Modifier.FINAL).addAnnotation(Getter.class).build());
                constructorBuilder.addStatement("this.$N = p.$N()", fieldName, getterName);
            } else {
                //messager.printMessage(Diagnostic.Kind.NOTE, "Skipping field without getter: " + fieldName, variableElement);
            }
        }


        TypeSpec.Builder eventClassBuilder = TypeSpec.classBuilder(eventName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(ClassName.get(Event.class))
                .addAnnotation(Data.class)
                .addAnnotation(AnnotationSpec.builder(EqualsAndHashCode.class).addMember("callSuper", "true").build())
                //.addAnnotation(AllArgsConstructor.class)
                .addFields(fields)
                .addMethod(constructorBuilder.build());

        JavaFile javaFile = JavaFile.builder(eventPackage, eventClassBuilder.build())
                .build();

        javaFile.writeTo(processingEnv.getFiler());
    }

    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        char first = name.charAt(0);
        if (Character.isUpperCase(first)) return name;
        return Character.toUpperCase(first) + name.substring(1);
    }
}

