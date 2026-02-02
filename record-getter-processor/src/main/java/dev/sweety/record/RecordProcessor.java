package dev.sweety.record;

import com.google.auto.service.AutoService;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import dev.sweety.record.annotations.DataIgnore;
import dev.sweety.record.annotations.RecordData;
import dev.sweety.record.annotations.RecordGetter;
import dev.sweety.record.annotations.Setter;
import dev.sweety.record.annotations.AllArgsConstructor;
import dev.sweety.record.annotations.SneakyThrows;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.HashSet;
import java.util.Set;

@SupportedAnnotationTypes({
        "dev.sweety.record.annotations.RecordGetter",
        "dev.sweety.record.annotations.Setter",
        "dev.sweety.record.annotations.RecordData",
        "dev.sweety.record.annotations.DataIgnore",
        "dev.sweety.record.annotations.AllArgsConstructor",
        "dev.sweety.record.annotations.SneakyThrows",
        "dev.sweety.record.annotations.UtilityClass"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@AutoService(Processor.class)
public class RecordProcessor extends AbstractProcessor {

    private TreeMaker maker;
    private Names names;
    private Trees trees; // Re-ordered slightly to match context, but field order doesn't matter much.

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.trees = Trees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.maker = TreeMaker.instance(context);
        this.names = Names.instance(context);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<Element> toProcess = new HashSet<>();

        // Collect elements
        Set<Element> annotatedElements = new HashSet<>();
        annotatedElements.addAll(roundEnv.getElementsAnnotatedWith(RecordData.class));
        annotatedElements.addAll(roundEnv.getElementsAnnotatedWith(RecordGetter.class));
        annotatedElements.addAll(roundEnv.getElementsAnnotatedWith(Setter.class));
        annotatedElements.addAll(roundEnv.getElementsAnnotatedWith(AllArgsConstructor.class));
        annotatedElements.addAll(roundEnv.getElementsAnnotatedWith(SneakyThrows.class));
        annotatedElements.removeAll(roundEnv.getElementsAnnotatedWith(DataIgnore.class));

        for (Element element : annotatedElements) {
            if (element.getKind() == ElementKind.FIELD) {
                toProcess.add(element.getEnclosingElement());
            } else if (element.getKind() == ElementKind.CLASS) {
                toProcess.add(element);
            } else if (element.getKind() == ElementKind.METHOD || element.getKind() == ElementKind.CONSTRUCTOR) {
                toProcess.add(element.getEnclosingElement());
            }
        }

        for (Element element : toProcess) {
            if (element instanceof TypeElement) {
                try {
                    injectMethods((TypeElement) element);
                } catch (Exception e) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Error processing: " + e.getMessage(), element);
                    e.printStackTrace();
                }
            }
        }

        return true;
    }

    private void injectMethods(TypeElement classElement) {
        JCTree tree = (JCTree) trees.getTree(classElement);
        if (!(tree instanceof JCTree.JCClassDecl classDecl)) return;

        RecordData dataAnnotation = classElement.getAnnotation(RecordData.class);
        RecordGetter getterAnnotation = classElement.getAnnotation(RecordGetter.class);
        Setter setterAnnotation = classElement.getAnnotation(Setter.class);
        AllArgsConstructor allArgsAnnotation = classElement.getAnnotation(AllArgsConstructor.class);

        // --- SneakyThrows ---
        boolean usesSneakyThrows = false;
        for (JCTree def : classDecl.defs) {
            if (def instanceof JCTree.JCMethodDecl method) {
                if (hasAnnotation(method.mods, SneakyThrows.class)) {
                    usesSneakyThrows = true;
                    if (method.body != null) {
                        wrapInSneakyThrows(method, classDecl.name);
                    }
                }
            }
        }

        if (usesSneakyThrows) {
            JCTree.JCMethodDecl sneakyMethod = createSneakyThrowMethod();
            if (!methodExists(classDecl, sneakyMethod)) {
                classDecl.defs = classDecl.defs.append(sneakyMethod);
            }
        }

        boolean applyAll = false;
        boolean includeStatic = false;
        boolean doSearchGetters = false;
        boolean doSearchSetters = false;

        // Nuovi flag
        Setter.Type[] setterTypes = {Setter.Type.DEFAULT};
        boolean allArgsConstructor = false;

        if (dataAnnotation != null) {
            applyAll = dataAnnotation.applyAll();
            includeStatic = dataAnnotation.includeStatic();
            doSearchGetters = true;
            doSearchSetters = true;
            setterTypes = dataAnnotation.setterTypes();
            allArgsConstructor = dataAnnotation.allArgsConstructor();
        } else if (getterAnnotation != null) {
            applyAll = getterAnnotation.applyAll();
            includeStatic = getterAnnotation.includeStatic();
            doSearchGetters = true;
        } else if (setterAnnotation != null) {
            applyAll = setterAnnotation.applyAll();
            includeStatic = setterAnnotation.includeStatic();
            doSearchSetters = true;
            setterTypes = setterAnnotation.types();
        }

        if (allArgsAnnotation != null) {
            allArgsConstructor = true;
        }

        // Genera AllArgsConstructor se richiesto
        if (allArgsConstructor) {
            JCTree.JCMethodDecl ctor = createAllArgsConstructor(classDecl);
            if (!methodExists(classDecl, ctor)) {
                classDecl.defs = classDecl.defs.append(ctor);
            }
        }

        List<JCTree> defs = classDecl.defs;
        for (JCTree def : defs) {
            if (def instanceof JCTree.JCVariableDecl field) {

                if (hasAnnotation(field.mods, DataIgnore.class)) continue;

                boolean fHasData = hasAnnotation(field.mods, RecordData.class);
                boolean fHasGetter = hasAnnotation(field.mods, RecordGetter.class);
                boolean fHasSetter = hasAnnotation(field.mods, Setter.class);

                boolean isStatic = (field.mods.flags & Flags.STATIC) != 0;
                boolean isFinal = (field.mods.flags & Flags.FINAL) != 0;

                boolean shouldGenGetter = fHasData || fHasGetter;
                if (!shouldGenGetter && doSearchGetters && applyAll) {
                    if (!isStatic || includeStatic) shouldGenGetter = true;
                }

                boolean shouldGenSetter = fHasData || fHasSetter;
                if (!shouldGenSetter && doSearchSetters && applyAll) {
                    if (!isStatic || includeStatic) shouldGenSetter = true;
                }

                if (shouldGenGetter) {
                    JCTree.JCMethodDecl method = createGetter(field, classDecl);
                    if (!methodExists(classDecl, method)) {
                        classDecl.defs = classDecl.defs.append(method);
                    }
                }

                if (shouldGenSetter && !isFinal) {
                    for (Setter.Type type : setterTypes) {
                        JCTree.JCMethodDecl method = createSetter(field, classDecl, type);
                        if (!methodExists(classDecl, method)) {
                            classDecl.defs = classDecl.defs.append(method);
                        }
                    }
                }
            }
        }
    }

    private boolean hasAnnotation(JCTree.JCModifiers mods, Class<?> annotationClass) {
        if (mods.annotations == null) return false;
        String simpleName = annotationClass.getSimpleName();
        for (JCTree.JCAnnotation ann : mods.annotations) {
            String annType = ann.annotationType.toString();
            if (annType.endsWith(simpleName)) return true;
        }
        return false;
    }

    private JCTree.JCMethodDecl createGetter(JCTree.JCVariableDecl field, JCTree.JCClassDecl classDecl) {
        boolean isStatic = (field.mods.flags & Flags.STATIC) != 0;
        String methodName = field.name.toString();

        long flags = Flags.PUBLIC;
        if (isStatic) flags |= Flags.STATIC;

        JCTree.JCExpression fieldAccess;
        if (isStatic) {
            fieldAccess = maker.Select(maker.Ident(classDecl.name), field.name);
        } else {
            fieldAccess = maker.Select(maker.Ident(names.fromString("this")), field.name);
        }

        JCTree.JCBlock body = maker.Block(0, List.of(maker.Return(fieldAccess)));

        return maker.MethodDef(
                maker.Modifiers(flags),
                names.fromString(methodName),
                field.vartype,
                List.nil(),
                List.nil(),
                List.nil(),
                body,
                null
        );
    }

    private JCTree.JCMethodDecl createSetter(JCTree.JCVariableDecl field, JCTree.JCClassDecl classDecl, Setter.Type type) {
        boolean isStatic = (field.mods.flags & Flags.STATIC) != 0;
        boolean classic = type.classic();
        boolean builder = type.builder();
        String fieldName = field.name.toString();
        String methodName;

        if (classic) {
            methodName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        } else {
            methodName = fieldName;
        }

        long flags = Flags.PUBLIC;
        if (isStatic) flags |= Flags.STATIC;

        JCTree.JCVariableDecl param = maker.VarDef(
                maker.Modifiers(Flags.PARAMETER),
                field.name,
                field.vartype,
                null
        );

        JCTree.JCExpression fieldAccess;
        if (isStatic) {
            fieldAccess = maker.Select(maker.Ident(classDecl.name), field.name);
        } else {
            fieldAccess = maker.Select(maker.Ident(names.fromString("this")), field.name);
        }

        JCTree.JCStatement assign = maker.Exec(
                maker.Assign(fieldAccess, maker.Ident(field.name))
        );

        List<JCTree.JCStatement> statements = List.of(assign);
        JCTree.JCExpression returnType;

        if (builder && !isStatic) {
            statements = statements.append(maker.Return(maker.Ident(names.fromString("this"))));
            returnType = maker.Ident(classDecl.name);
        } else {
            returnType = maker.TypeIdent(TypeTag.VOID);
        }

        JCTree.JCBlock body = maker.Block(0, statements);

        return maker.MethodDef(
                maker.Modifiers(flags),
                names.fromString(methodName),
                returnType,
                List.nil(),
                List.of(param),
                List.nil(),
                body,
                null
        );
    }

    private JCTree.JCMethodDecl createAllArgsConstructor(JCTree.JCClassDecl classDecl) {
        List<JCTree.JCVariableDecl> params = List.nil();
        List<JCTree.JCStatement> assignments = List.nil();

        for (JCTree def : classDecl.defs) {
            if (!(def instanceof JCTree.JCVariableDecl field)) continue;

            boolean isStatic = (field.mods.flags & Flags.STATIC) != 0;

            if (isStatic || hasAnnotation(field.mods, DataIgnore.class)) continue;

            // Crea parametro
            params = params.append(maker.VarDef(maker.Modifiers(Flags.PARAMETER), field.name, field.vartype, null));

            // Crea assegnamento this.field = field;
            JCTree.JCExpression thisField = maker.Select(maker.Ident(names.fromString("this")), field.name);
            JCTree.JCStatement assign = maker.Exec(maker.Assign(thisField, maker.Ident(field.name)));
            assignments = assignments.append(assign);
        }

        JCTree.JCBlock body = maker.Block(0, assignments);

        return maker.MethodDef(
                maker.Modifiers(Flags.PUBLIC),
                names.fromString("<init>"),
                null,
                List.nil(),
                params,
                List.nil(),
                body,
                null
        );
    }

    private boolean methodExists(JCTree.JCClassDecl classDecl, JCTree.JCMethodDecl method) {
        for (JCTree def : classDecl.defs) {
            if (def instanceof JCTree.JCMethodDecl existing) {
                if (existing.name.equals(method.name)) {
                    // For $sneakyThrow, we just check name to avoid duplicate injection
                    if (existing.name.contentEquals("$sneakyThrow")) return true;
                    if (existing.params.length() == method.params.length()) { // Simplistic signature check
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void wrapInSneakyThrows(JCTree.JCMethodDecl method, Name className) {
        // Generates:
        // try { originalBody } catch (Throwable t) { ClassName.<RuntimeException>$sneakyThrow(t); }

        JCTree.JCBlock originalBody = method.body;

        JCTree.JCVariableDecl catchParam = maker.VarDef(
                maker.Modifiers(0),
                names.fromString("t"),
                maker.Ident(names.fromString("Throwable")),
                null
        );

        JCTree.JCExpression methodSelect = maker.Select(maker.Ident(className), names.fromString("$sneakyThrow"));
        JCTree.JCExpression call = maker.Apply(
                List.of(maker.Ident(names.fromString("RuntimeException"))), // Type args
                methodSelect,
                List.of(maker.Ident(names.fromString("t")))
        );

        JCTree.JCStatement callStmt = maker.Exec(call);
        JCTree.JCBlock catchBlock = maker.Block(0, List.of(callStmt));
        JCTree.JCCatch catcher = maker.Catch(catchParam, catchBlock);

        JCTree.JCTry tryCatch = maker.Try(
                originalBody,
                List.of(catcher),
                null
        );

        method.body = maker.Block(0, List.of(tryCatch));
    }

    private JCTree.JCMethodDecl createSneakyThrowMethod() {
        // private static <T extends Throwable> void $sneakyThrow(Throwable t) throws T {
        //    throw (T) t;
        // }

        Name nameT = names.fromString("T");
        Name nameSneaky = names.fromString("$sneakyThrow");
        Name nameParam = names.fromString("t");

        // <T extends Throwable>
        JCTree.JCTypeParameter typeParam = maker.TypeParameter(nameT, List.of(maker.Ident(names.fromString("Throwable"))));

        // Param: Throwable t
        JCTree.JCVariableDecl param = maker.VarDef(
                maker.Modifiers(Flags.PARAMETER),
                nameParam,
                maker.Ident(names.fromString("Throwable")),
                null
        );

        // Body: throw (T) t;
        JCTree.JCExpression cast = maker.TypeCast(maker.Ident(nameT), maker.Ident(nameParam));
        JCTree.JCStatement throwStmt = maker.Throw(cast);
        JCTree.JCBlock body = maker.Block(0, List.of(throwStmt));

        return maker.MethodDef(
                maker.Modifiers(Flags.PRIVATE | Flags.STATIC),
                nameSneaky,
                maker.TypeIdent(TypeTag.VOID),
                List.of(typeParam),
                List.of(param),
                List.of(maker.Ident(nameT)), // throws T
                body,
                null
        );
    }

    private JCTree.JCMethodDecl createPrivateConstructor(JCTree.JCClassDecl classDecl) {
        JCTree.JCStatement throwStmt = maker.Throw(
                maker.NewClass(
                        null,
                        List.nil(),
                        maker.Ident(names.fromString("UnsupportedOperationException")),
                        List.of(maker.Literal("This is a utility class and cannot be instantiated")),
                        null
                )
        );

        JCTree.JCBlock body = maker.Block(0, List.of(throwStmt));

        return maker.MethodDef(
                maker.Modifiers(Flags.PUBLIC),
                names.fromString("<init>"),
                null,
                List.nil(),
                List.nil(),
                List.nil(),
                body,
                null
        );
    }
}
