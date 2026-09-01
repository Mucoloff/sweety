package dev.sweety.event.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dev.sweety.event.api.AbstractCancellableEvent;
import dev.sweety.event.api.AbstractEvent;
import dev.sweety.event.api.CancellableEvent;
import dev.sweety.event.api.Event;
import dev.sweety.event.api.MutableEvent;
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
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("dev.sweety.event.processor.GenerateEvent")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@AutoService(Processor.class)
public class EventProcessor extends AbstractProcessor {

    private Messager messager;
    private Elements elementUtils;
    private EventFieldScanner fieldScanner;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.elementUtils = processingEnv.getElementUtils();
        Types typeUtils = processingEnv.getTypeUtils();
        this.fieldScanner = new EventFieldScanner(typeUtils);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(GenerateEvent.class)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Can only be applied to interfaces", element);
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

    private void generateEventStructure(TypeElement interfaceElement) throws IOException {
        String packageName = elementUtils.getPackageOf(interfaceElement).getQualifiedName().toString();
        GenerateEvent config = interfaceElement.getAnnotation(GenerateEvent.class);

        String templateName = interfaceElement.getSimpleName().toString();

        String cleanName = (config != null && !config.value().isEmpty()) ? config.value() : templateName;
        if (cleanName.endsWith("Template")) cleanName = cleanName.substring(0, cleanName.length() - 8);
        else if (cleanName.endsWith("Def")) cleanName = cleanName.substring(0, cleanName.length() - 3);

        String eventName = cleanName.endsWith("Event") ? cleanName : cleanName + "Event";
        String mutableName = "Mutable" + eventName;
        String immutableImplName = eventName + "Impl";
        String mutableImplName = "Mutable" + eventName + "Impl";
        String factoryName = eventName + "Factory";

        boolean genImmutable = config == null || config.type().immutable();
        boolean genMutable = config == null || config.type().mutable();

        boolean isTemplateTheInterface = templateName.equals(eventName);
        boolean isCancellable = fieldScanner.isCancellable(interfaceElement);
        List<EventFieldScanner.FieldInfo> fields = fieldScanner.extractFields(interfaceElement);

        ClassName eventClass = ClassName.get(packageName, eventName);
        ClassName mutableClass = ClassName.get(packageName, mutableName);

        // 1. Generate Immutable Interface if template name != event name
        if (!isTemplateTheInterface && genImmutable) {
            TypeName superIface = isCancellable
                    ? ParameterizedTypeName.get(ClassName.get(CancellableEvent.class), eventClass)
                    : ParameterizedTypeName.get(ClassName.get(Event.class), eventClass);

            TypeSpec.Builder readOnlyInterfaceBuilder = TypeSpec.interfaceBuilder(eventName)
                    .addModifiers(Modifier.PUBLIC)
                    .addSuperinterface(superIface);

            for (EventFieldScanner.FieldInfo field : fields) {
                readOnlyInterfaceBuilder.addMethod(MethodSpec.methodBuilder(field.getterName())
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(field.type())
                        .build());
            }
            writeJavaFile(packageName, readOnlyInterfaceBuilder.build());
        }

        // 2. Generate Mutable Interface
        if (genMutable) {
            TypeSpec.Builder mutableInterfaceBuilder = TypeSpec.interfaceBuilder(mutableName)
                    .addModifiers(Modifier.PUBLIC)
                    .addSuperinterface(eventClass)
                    .addSuperinterface(ParameterizedTypeName.get(ClassName.get(MutableEvent.class), eventClass));

            for (EventFieldScanner.FieldInfo field : fields) {
                mutableInterfaceBuilder.addMethod(MethodSpec.methodBuilder(field.setterName())
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .addParameter(field.type(), field.name())
                        .returns(TypeName.VOID)
                        .build());
            }

            mutableInterfaceBuilder.addMethod(MethodSpec.methodBuilder("post")
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .addAnnotation(Override.class)
                    .addAnnotation(NotNull.class)
                    .returns(mutableClass)
                    .build());

            writeJavaFile(packageName, mutableInterfaceBuilder.build());
        }

        // 3. Generate Immutable Impl Class
        if (genImmutable) {
            TypeName superClass = isCancellable
                    ? ParameterizedTypeName.get(ClassName.get(AbstractCancellableEvent.class), eventClass)
                    : ParameterizedTypeName.get(ClassName.get(AbstractEvent.class), eventClass);

            TypeSpec.Builder implBuilder = TypeSpec.classBuilder(immutableImplName)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .superclass(superClass)
                    .addSuperinterface(eventClass);

            for (EventFieldScanner.FieldInfo field : fields) {
                implBuilder.addField(FieldSpec.builder(field.type(), field.name(), Modifier.PRIVATE, Modifier.FINAL).build());
            }

            MethodSpec.Builder ctor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
            for (EventFieldScanner.FieldInfo field : fields) {
                ctor.addParameter(field.type(), field.name());
                ctor.addStatement("this.$N = $N", field.name(), field.name());
            }
            implBuilder.addMethod(ctor.build());

            for (EventFieldScanner.FieldInfo field : fields) {
                implBuilder.addMethod(MethodSpec.methodBuilder(field.getterName())
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(field.type())
                        .addStatement("return this.$N", field.name())
                        .build());
            }

            writeJavaFile(packageName, implBuilder.build());
        }

        // 4. Generate Mutable Impl Class
        if (genMutable) {
            TypeName superClass = isCancellable
                    ? ParameterizedTypeName.get(ClassName.get(AbstractCancellableEvent.class), eventClass)
                    : ParameterizedTypeName.get(ClassName.get(AbstractEvent.class), eventClass);

            TypeSpec.Builder mutableImplBuilder = TypeSpec.classBuilder(mutableImplName)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .superclass(superClass)
                    .addSuperinterface(mutableClass);

            for (EventFieldScanner.FieldInfo field : fields) {
                mutableImplBuilder.addField(FieldSpec.builder(field.type(), field.name(), Modifier.PRIVATE).build());
            }

            MethodSpec.Builder mutableCtor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
            for (EventFieldScanner.FieldInfo field : fields) {
                mutableCtor.addParameter(field.type(), field.name());
                mutableCtor.addStatement("this.$N = $N", field.name(), field.name());
            }
            mutableImplBuilder.addMethod(mutableCtor.build());

            for (EventFieldScanner.FieldInfo field : fields) {
                mutableImplBuilder.addMethod(MethodSpec.methodBuilder(field.getterName())
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override.class)
                        .returns(field.type())
                        .addStatement("return this.$N", field.name())
                        .build());

                mutableImplBuilder.addMethod(MethodSpec.methodBuilder(field.setterName())
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override.class)
                        .addParameter(field.type(), field.name())
                        .addStatement("this.$N = $N", field.name(), field.name())
                        .build());
            }

            mutableImplBuilder.addMethod(MethodSpec.methodBuilder("post")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .addAnnotation(NotNull.class)
                    .returns(mutableClass)
                    .addStatement("this.pre = false")
                    .addStatement("return this")
                    .build());

            if (genImmutable) {
                ClassName factoryClass = ClassName.get(packageName, factoryName);
                MethodSpec.Builder toImmutable = MethodSpec.methodBuilder("toImmutable")
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override.class)
                        .addAnnotation(NotNull.class)
                        .returns(eventClass);
                StringBuilder args = new StringBuilder();
                for (int i = 0; i < fields.size(); i++) {
                    if (i > 0) args.append(", ");
                    args.append("this.").append(fields.get(i).name());
                }
                toImmutable.addStatement("return $T.of($L)", factoryClass, args.toString());
                mutableImplBuilder.addMethod(toImmutable.build());
            }

            writeJavaFile(packageName, mutableImplBuilder.build());
        }

        // 5. Generate Factory Class
        TypeSpec.Builder factory = TypeSpec.classBuilder(factoryName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

        ClassName implImmutableClass = ClassName.get(packageName, immutableImplName);
        ClassName implMutableClass = ClassName.get(packageName, mutableImplName);

        if (genImmutable) {
            MethodSpec.Builder of = MethodSpec.methodBuilder("of")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(eventClass);
            for (EventFieldScanner.FieldInfo field : fields) of.addParameter(field.type(), field.name());
            StringBuilder args = new StringBuilder();
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) args.append(", ");
                args.append(fields.get(i).name());
            }
            of.addStatement("return new $T($L)", implImmutableClass, args.toString());
            factory.addMethod(of.build());
        }

        if (genMutable) {
            MethodSpec.Builder ofMutable = MethodSpec.methodBuilder("ofMutable")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(mutableClass);
            for (EventFieldScanner.FieldInfo field : fields) ofMutable.addParameter(field.type(), field.name());
            StringBuilder args = new StringBuilder();
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) args.append(", ");
                args.append(fields.get(i).name());
            }
            ofMutable.addStatement("return new $T($L)", implMutableClass, args.toString());
            factory.addMethod(ofMutable.build());
        }

        writeJavaFile(packageName, factory.build());
    }

    private void writeJavaFile(String packageName, TypeSpec typeSpec) throws IOException {
        JavaFile.builder(packageName, typeSpec).build().writeTo(processingEnv.getFiler());
    }
}
