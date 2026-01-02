package dev.sweety.record;

import com.google.auto.service.AutoService;
import dev.sweety.record.annotations.RecordData;
import dev.sweety.record.annotations.RecordGetter;
import dev.sweety.record.annotations.RecordSetter;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes(
        {"dev.sweety.record.annotations.RecordGetter",
                "dev.sweety.record.annotations.RecordSetter",
                "dev.sweety.record.annotations.RecordData"}
)
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@AutoService(Processor.class)
public class RecordProcessor extends AbstractProcessor {

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
        Set<Element> toProcess = new HashSet<>(), annotatedElements = new HashSet<>();

        annotatedElements.addAll(roundEnv.getElementsAnnotatedWith(RecordData.class));
        annotatedElements.addAll(roundEnv.getElementsAnnotatedWith(RecordGetter.class));
        annotatedElements.addAll(roundEnv.getElementsAnnotatedWith(RecordSetter.class));

        for (Element element : annotatedElements) {
            boolean isField = element.getKind().equals(ElementKind.FIELD);
            if (element.getKind() != ElementKind.CLASS && !isField) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Can't be applied to " + element.getKind(), element);
                return true;
            }

            toProcess.add(isField ? element.getEnclosingElement() : element);
        }

        for (Element element : toProcess) {
            try {
                generateInterface((TypeElement) element);
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Couldn't generate event class", element);
            }
        }

        return true;
    }

    private void generateInterface(TypeElement classElement) throws IOException {
        RecordData dataAnnotation = classElement.getAnnotation(RecordData.class);
        RecordGetter getterAnnotation = classElement.getAnnotation(RecordGetter.class);
        RecordSetter settetAnnotation = classElement.getAnnotation(RecordSetter.class);

        final boolean applyAll, includeStatic, getters, setters;
        if (dataAnnotation != null) {
            applyAll = dataAnnotation.applyAll();
            includeStatic = dataAnnotation.includeStatic();
            getters = setters = true;
        } else if (getterAnnotation != null) {
            applyAll = getterAnnotation.applyAll();
            includeStatic = getterAnnotation.includeStatic();
            getters = true;
            setters = false;
        } else if (settetAnnotation != null) {
            applyAll = settetAnnotation.applyAll();
            includeStatic = settetAnnotation.includeStatic();
            getters = false;
            setters = true;
        } else applyAll = includeStatic = getters = setters = false;

        String className = classElement.getSimpleName().toString();
        String interfaceName = className + "Accessors";
        String packageName = elementUtils.getPackageOf(classElement).getQualifiedName().toString();

        JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(packageName + "." + interfaceName, classElement);

        try (PrintWriter writer = new PrintWriter(sourceFile.openWriter())) {
            writer.println("package " + packageName + ";");
            writer.println();
            writer.println("import dev.sweety.record.DataUtils;");
            writer.println("import lombok.SneakyThrows;");
            writer.println();
            writer.println("public interface " + interfaceName + " {");
            writer.println();

            List<Element> fields = classElement.getEnclosedElements().stream()
                    .filter(e -> e.getKind() == ElementKind.FIELD)
                    .collect(Collectors.toList());


            for (Element field : fields) {
                String fieldName = field.getSimpleName().toString();
                String fieldType = field.asType().toString();
                boolean isStatic = field.getModifiers().contains(Modifier.STATIC);
                boolean isPrivate = field.getModifiers().contains(Modifier.PRIVATE);
                boolean isFinal = field.getModifiers().contains(Modifier.FINAL);

                boolean hasData = field.getAnnotation(RecordData.class) != null;
                boolean hasGetter = field.getAnnotation(RecordGetter.class) != null;
                boolean hasSetter = field.getAnnotation(RecordSetter.class) != null;

                boolean hasAnnotation = hasData || hasGetter || hasSetter;

                if (hasAnnotation || (applyAll && (!isStatic || includeStatic))) {

                    if (getters || hasData || hasGetter)
                        getter(isPrivate, writer, isStatic, fieldType, fieldName, className);
                    if (setters || hasData || hasSetter) {
                        if (!isFinal) setter(isPrivate, writer, isStatic, fieldType, fieldName, className);
                        else if (hasSetter)
                            messager.printError("Cannot generate setter for final field: " + fieldName, field);
                    }
                }
            }

            writer.println("}");
        }
    }

    private void getter(boolean isPrivate, PrintWriter writer, boolean isStatic, String fieldType, String fieldName, String className) {
        Formats format;
        if (isPrivate)
            if (isStatic) format = Formats.PRIVATE_STATIC_GETTER;
            else format = Formats.PRIVATE_GETTER;
        else if (isStatic) format = Formats.STATIC_GETTER;
        else format = Formats.GETTER;

        writer.println("    " + format.apply(fieldType, fieldName, className));
    }

    private void setter(boolean isPrivate, PrintWriter writer, boolean isStatic, String fieldType, String fieldName, String className) {
        Formats format;
        if (isPrivate)
            if (isStatic) format = Formats.PRIVATE_STATIC_SETTER;
            else format = Formats.PRIVATE_SETTER;
        else if (isStatic) format = Formats.STATIC_SETTER;
        else format = Formats.SETTER;

        writer.println("    " + format.apply(fieldType, fieldName, className));
    }
}

