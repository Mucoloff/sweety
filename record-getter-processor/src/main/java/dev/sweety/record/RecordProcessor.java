package dev.sweety.record;

import com.google.auto.service.AutoService;

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

@SupportedAnnotationTypes("dev.sweety.record.RecordGetter")
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
        Set<Element> toProcess = new HashSet<>();
        for (Element element : roundEnv.getElementsAnnotatedWith(RecordGetter.class)) {
            boolean isField = element.getKind().equals(ElementKind.FIELD);
            if (element.getKind() != ElementKind.CLASS && !isField) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Can't be applied to " + element.getKind(), element);
                return true;
            }

            if (isField) {
                toProcess.add(element.getEnclosingElement());
                continue;
            }

            toProcess.add(element);
        }

        for (Element element : toProcess) {
            try {
                generateGetterInterface((TypeElement) element);
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Couldn't generate event class", element);
            }
        }

        return true;
    }

    private void generateGetterInterface(TypeElement classElement) throws IOException {
        RecordGetter classAnnotation = classElement.getAnnotation(RecordGetter.class);

        final boolean applyAll, includeStatic;
        if (classAnnotation != null) {
            applyAll = classAnnotation.applyAll();
            includeStatic = classAnnotation.includeStatic();
        } else applyAll = includeStatic = false;

        String className = classElement.getSimpleName().toString();
        String interfaceName = className + "Getters";
        String packageName = elementUtils.getPackageOf(classElement).getQualifiedName().toString();

        JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(packageName + "." + interfaceName, classElement);

        try (PrintWriter writer = new PrintWriter(sourceFile.openWriter())) {
            writer.println("package " + packageName + ";");
            writer.println();
            writer.println("import dev.sweety.record.GetterUtils;");
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

                boolean hasAnnotation = field.getAnnotation(RecordGetter.class) != null;

                if (hasAnnotation || (applyAll && (!isStatic || includeStatic))) {
                    writer.println("    @SneakyThrows");

                    writer.print("    ");
                    writer.print(isStatic ? "static" : "default");
                    writer.println(" " + fieldType + " " + fieldName + "() {");

                    if (isPrivate) {
                        writer.print("        return (" + fieldType + ") GetterUtils.get(" + className + ".class, \"" + fieldName);
                        writer.println(isStatic ? "\");" : "\", this);");
                    } else {
                        writer.println("        return " + (isStatic ? className : "((" + className + ") this)") + "." + fieldName + ";");
                    }


                    writer.println("    }");
                    writer.println();
                }
            }

            writer.println("}");
        }
    }
}

