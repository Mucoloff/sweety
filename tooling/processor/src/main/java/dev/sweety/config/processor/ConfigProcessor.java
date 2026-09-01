package dev.sweety.config.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dev.sweety.config.annotation.ConfigKey;
import dev.sweety.config.annotation.GenerateConfig;
import dev.sweety.config.common.ConfigurationSection;
import dev.sweety.processor.Ignore;

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
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("dev.sweety.config.annotation.GenerateConfig")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@AutoService(Processor.class)
public class ConfigProcessor extends AbstractProcessor {

    private Messager messager;
    private Elements elementUtils;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.elementUtils = processingEnv.getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(GenerateConfig.class)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@GenerateConfig can only be applied to interfaces", element);
                return true;
            }
            try {
                generateConfigImpl((TypeElement) element);
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate config impl: " + e.getMessage(), element);
            }
        }
        return true;
    }

    private void generateConfigImpl(TypeElement interfaceElement) throws IOException {
        String packageName = elementUtils.getPackageOf(interfaceElement).getQualifiedName().toString();
        String interfaceName = interfaceElement.getSimpleName().toString();
        String implName = interfaceName.endsWith("Config") ? interfaceName + "Impl" : interfaceName + "ConfigImpl";

        ClassName ifaceClass = ClassName.get(packageName, interfaceName);
        ClassName implClass = ClassName.get(packageName, implName);

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(implName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(ifaceClass);

        MethodSpec.Builder defaultCtor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
        MethodSpec.Builder loadMethod = MethodSpec.methodBuilder("load")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get(ConfigurationSection.class), "section");

        MethodSpec.Builder saveMethod = MethodSpec.methodBuilder("save")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get(ConfigurationSection.class), "section");

        List<String> fieldNames = new ArrayList<>();

        for (Element enclosed : interfaceElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) continue;
            ExecutableElement method = (ExecutableElement) enclosed;

            // Strict Filter: abstract, 0 params, non-void, non-ignored
            if (method.isDefault() || method.getModifiers().contains(Modifier.DEFAULT) || method.getModifiers().contains(Modifier.STATIC)) continue;
            if (!method.getParameters().isEmpty() || method.getReturnType().getKind() == TypeKind.VOID) continue;
            if (method.getAnnotation(Ignore.class) != null) continue;

            String propName = method.getSimpleName().toString();
            ConfigKey keyAnn = method.getAnnotation(ConfigKey.class);
            String configKey = (keyAnn != null && !keyAnn.value().isEmpty()) ? keyAnn.value() : propName;

            TypeMirror returnType = method.getReturnType();
            TypeName typeName = TypeName.get(returnType);
            fieldNames.add(propName);

            // Add private field
            classBuilder.addField(FieldSpec.builder(typeName, propName, Modifier.PRIVATE).build());

            // Getter
            classBuilder.addMethod(MethodSpec.methodBuilder(propName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .returns(typeName)
                    .addStatement("return this.$N", propName)
                    .build());

            // Setter
            String setterName = "set" + Character.toUpperCase(propName.charAt(0)) + propName.substring(1);
            classBuilder.addMethod(MethodSpec.methodBuilder(setterName)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(typeName, propName)
                    .addStatement("this.$N = $N", propName, propName)
                    .build());

            // Load and Save bindings
            addLoadBinding(loadMethod, propName, configKey, returnType);
            addSaveBinding(saveMethod, propName, configKey, returnType);
        }

        // Section constructor
        MethodSpec.Builder sectionCtor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get(ConfigurationSection.class), "section")
                .addStatement("load(section)");

        classBuilder.addMethod(defaultCtor.build())
                .addMethod(sectionCtor.build())
                .addMethod(loadMethod.build())
                .addMethod(saveMethod.build());

        JavaFile.builder(packageName, classBuilder.build()).build().writeTo(processingEnv.getFiler());
    }

    private void addLoadBinding(MethodSpec.Builder mb, String fieldName, String key, TypeMirror type) {
        switch (type.getKind()) {
            case BOOLEAN -> mb.addStatement("this.$N = section.getBoolean($S, this.$N)", fieldName, key, fieldName);
            case INT -> mb.addStatement("this.$N = section.getInt($S, this.$N)", fieldName, key, fieldName);
            case LONG -> mb.addStatement("this.$N = section.getLong($S, this.$N)", fieldName, key, fieldName);
            case DOUBLE -> mb.addStatement("this.$N = section.getDouble($S, this.$N)", fieldName, key, fieldName);
            case FLOAT -> mb.addStatement("this.$N = (float) section.getDouble($S, (double) this.$N)", fieldName, key, fieldName);
            case DECLARED -> {
                if (type.toString().equals("java.lang.String")) {
                    mb.addStatement("this.$N = section.getString($S, this.$N)", fieldName, key, fieldName);
                } else {
                    mb.addStatement("this.$N = (section.get($S) != null) ? ($T) section.get($S) : this.$N", fieldName, key, TypeName.get(type), key, fieldName);
                }
            }
            default -> mb.addStatement("this.$N = ($T) section.get($S)", fieldName, TypeName.get(type), key);
        }
    }

    private void addSaveBinding(MethodSpec.Builder mb, String fieldName, String key, TypeMirror type) {
        mb.addStatement("section.set($S, this.$N)", key, fieldName);
    }
}
