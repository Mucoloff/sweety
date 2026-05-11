package dev.sweety.event.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import dev.sweety.event.api.*;
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
import java.util.Set;
import java.util.Objects;

@SupportedAnnotationTypes("dev.sweety.event.processor.GenerateEvent")
@SupportedSourceVersion(SourceVersion.RELEASE_24)
@AutoService(Processor.class)
public class EventProcessor extends AbstractProcessor {

    private Messager messager;
    private Elements elementUtils;
    private Types typeUtils;
    
    private TreeMaker maker;
    private Names names;
    private Trees trees;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        elementUtils = processingEnv.getElementUtils();
        typeUtils = processingEnv.getTypeUtils();
        
        this.trees = Trees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.maker = TreeMaker.instance(context);
        this.names = Names.instance(context);
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
        String baseName = (config != null && !config.value().isEmpty()) ? config.value() : templateName;
        
        String cleanName = baseName;
        if (cleanName.endsWith("Template")) cleanName = cleanName.substring(0, cleanName.length() - 8);
        else if (cleanName.endsWith("Def")) cleanName = cleanName.substring(0, cleanName.length() - 3);

        String eventInterfaceName = cleanName.endsWith("Event") ? cleanName : cleanName + "Event";
        String mutableInterfaceName = "Mutable" + eventInterfaceName;
        String immutableImplName = eventInterfaceName + "Impl";
        String mutableImplName = "Mutable" + eventInterfaceName + "Impl";

        ClassName eventInterface = ClassName.get(packageName, eventInterfaceName);
        ClassName mutableInterface = ClassName.get(packageName, mutableInterfaceName);

        java.util.List<FieldInfo> fields = extractFieldsFromInterface(interfaceElement);
        boolean isCancellable = isCancellable(interfaceElement);

        // 1. Generate interfaces if they don't match the template
        boolean isTemplateTheInterface = templateName.equals(eventInterfaceName);
        if (!isTemplateTheInterface && (config == null || config.immutable())) {
            TypeSpec.Builder readOnlyInterfaceBuilder = TypeSpec.interfaceBuilder(eventInterfaceName)
                    .addModifiers(Modifier.PUBLIC)
                    .addSuperinterface(ParameterizedTypeName.get(ClassName.get(isCancellable ? CancellableEvent.class : Event.class), eventInterface));
            for (FieldInfo field : fields) readOnlyInterfaceBuilder.addMethod(MethodSpec.methodBuilder(field.getterName).addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).returns(field.type).build());
            writeJavaFile(packageName, readOnlyInterfaceBuilder.build());
        }

        if (config == null || config.mutable()) {
            TypeSpec.Builder mutableInterfaceBuilder = TypeSpec.interfaceBuilder(mutableInterfaceName).addModifiers(Modifier.PUBLIC).addSuperinterface(eventInterface).addSuperinterface(ParameterizedTypeName.get(ClassName.get(MutableEvent.class), eventInterface));
            for (FieldInfo field : fields) mutableInterfaceBuilder.addMethod(MethodSpec.methodBuilder(field.setterName).addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).addParameter(field.type, field.name).build());
            mutableInterfaceBuilder.addMethod(MethodSpec.methodBuilder("post").addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT).addAnnotation(Override.class).addAnnotation(NotNull.class).returns(mutableInterface).build());
            writeJavaFile(packageName, mutableInterfaceBuilder.build());
        }

        // 2. Inject components into the interface (Inner classes and static methods)
        injectEventComponents(interfaceElement, fields, eventInterface, mutableInterface, immutableImplName, mutableImplName, isCancellable, config);
    }

    private void injectEventComponents(TypeElement interfaceElement, java.util.List<FieldInfo> fields, ClassName eventInterface, ClassName mutableInterface, String immutableImplName, String mutableImplName, boolean isCancellable, GenerateEvent config) {
        JCTree tree = (JCTree) trees.getTree(interfaceElement);
        if (!(tree instanceof JCTree.JCClassDecl classDecl)) return;

        maker.at(classDecl.pos);

        // Inject Impl
        if (config == null || config.immutable()) {
            JCTree.JCClassDecl implClass = createInnerClass(immutableImplName, fields, eventInterface, null, isCancellable, false);
            classDecl.defs = classDecl.defs.append(implClass);
            
            JCTree.JCMethodDecl ofMethod = createFactoryMethod("of", fields, eventInterface, immutableImplName);
            if (!methodExists(classDecl, ofMethod)) classDecl.defs = classDecl.defs.append(ofMethod);
        }

        // Inject MutableImpl
        if (config == null || config.mutable()) {
            JCTree.JCClassDecl mutableImplClass = createInnerClass(mutableImplName, fields, eventInterface, mutableInterface, isCancellable, true);
            classDecl.defs = classDecl.defs.append(mutableImplClass);

            JCTree.JCMethodDecl ofMutableMethod = createFactoryMethod("ofMutable", fields, mutableInterface, mutableImplName);
            if (!methodExists(classDecl, ofMutableMethod)) classDecl.defs = classDecl.defs.append(ofMutableMethod);
        }
    }

    private JCTree.JCClassDecl createInnerClass(String className, java.util.List<FieldInfo> fields, ClassName eventInterface, ClassName mutableInterface, boolean isCancellable, boolean mutable) {
        List<JCTree> defs = List.nil();
        
        // Fields
        for (FieldInfo field : fields) {
            defs = defs.append(maker.VarDef(maker.Modifiers(mutable ? Flags.PRIVATE : Flags.PRIVATE | Flags.FINAL), names.fromString(field.name), typeToExpression(field.type), null));
        }

        // Constructor (Private)
        List<JCTree.JCVariableDecl> params = List.nil();
        List<JCTree.JCStatement> stats = List.nil();
        for (FieldInfo field : fields) {
            params = params.append(maker.VarDef(maker.Modifiers(Flags.PARAMETER), names.fromString(field.name), typeToExpression(field.type), null));
            stats = stats.append(maker.Exec(maker.Assign(maker.Select(maker.Ident(names.fromString("this")), names.fromString(field.name)), maker.Ident(names.fromString(field.name)))));
        }
        defs = defs.append(maker.MethodDef(maker.Modifiers(Flags.PRIVATE), names.init, maker.TypeIdent(TypeTag.VOID), List.nil(), params, List.nil(), maker.Block(0, stats), null));

        // Getters
        for (FieldInfo field : fields) {
            defs = defs.append(maker.MethodDef(maker.Modifiers(Flags.PUBLIC), names.fromString(field.getterName), typeToExpression(field.type), List.nil(), List.nil(), List.nil(), maker.Block(0, List.of(maker.Return(maker.Select(maker.Ident(names.fromString("this")), names.fromString(field.name))))), null));
            if (mutable) {
                // Setter
                List<JCTree.JCVariableDecl> setterParams = List.of(maker.VarDef(maker.Modifiers(Flags.PARAMETER), names.fromString(field.name), typeToExpression(field.type), null));
                List<JCTree.JCStatement> setterStats = List.of(
                    maker.Exec(maker.Assign(maker.Select(maker.Ident(names.fromString("this")), names.fromString(field.name)), maker.Ident(names.fromString(field.name))))
                );
                defs = defs.append(maker.MethodDef(maker.Modifiers(Flags.PUBLIC), names.fromString(field.setterName), maker.TypeIdent(TypeTag.VOID), List.nil(), setterParams, List.nil(), maker.Block(0, setterStats), null));
            }
        }

        // post() and toImmutable() if mutable
        if (mutable) {
            // post()
            defs = defs.append(maker.MethodDef(maker.Modifiers(Flags.PUBLIC), names.fromString("post"), typeToExpression(mutableInterface), List.nil(), List.nil(), List.nil(), maker.Block(0, List.of(maker.Exec(maker.Assign(maker.Select(maker.Ident(names.fromString("this")), names.fromString("pre")), maker.Literal(false))), maker.Return(maker.Ident(names.fromString("this"))))), null));
            // toImmutable()
            List<JCTree.JCExpression> factoryArgs = List.nil();
            for (FieldInfo field : fields) factoryArgs = factoryArgs.append(maker.Select(maker.Ident(names.fromString("this")), names.fromString(field.name)));
            JCTree.JCMethodInvocation ofCall = maker.Apply(List.nil(), maker.Select(typeToExpression(eventInterface), names.fromString("of")), factoryArgs);
            defs = defs.append(maker.MethodDef(maker.Modifiers(Flags.PUBLIC), names.fromString("toImmutable"), typeToExpression(eventInterface), List.nil(), List.nil(), List.nil(), maker.Block(0, List.of(maker.Return(ofCall))), null));
        }

        // Super class
        String superClassName = isCancellable ? "dev.sweety.event.api.AbstractCancellableEvent" : "dev.sweety.event.api.AbstractEvent";
        JCTree.JCExpression superClass = typeToExpression(ParameterizedTypeName.get(ClassName.bestGuess(superClassName), eventInterface));

        List<JCTree.JCExpression> implementing = List.of(typeToExpression(mutable ? mutableInterface : eventInterface));

        return maker.ClassDef(
                maker.Modifiers(Flags.STATIC | Flags.FINAL), // Implicitly public in interface
                names.fromString(className),
                List.nil(),
                superClass,
                implementing,
                defs
        );
    }

    private JCTree.JCMethodDecl createFactoryMethod(String methodName, java.util.List<FieldInfo> fields, ClassName returnType, String implClassName) {
        List<JCTree.JCVariableDecl> params = List.nil();
        List<JCTree.JCExpression> args = List.nil();
        for (FieldInfo field : fields) {
            params = params.append(maker.VarDef(maker.Modifiers(Flags.PARAMETER), names.fromString(field.name), typeToExpression(field.type), null));
            args = args.append(maker.Ident(names.fromString(field.name)));
        }

        JCTree.JCExpression newExpr = maker.NewClass(null, List.nil(), maker.Ident(names.fromString(implClassName)), args, null);
        JCTree.JCBlock body = maker.Block(0, List.of(maker.Return(newExpr)));

        return maker.MethodDef(
                maker.Modifiers(Flags.PUBLIC | Flags.STATIC),
                names.fromString(methodName),
                typeToExpression(returnType),
                List.nil(),
                params,
                List.nil(),
                body,
                null
        );
    }

    private JCTree.JCExpression typeToExpression(TypeName typeName) {
        if (typeName.isPrimitive()) {
            if (typeName.equals(TypeName.INT)) return maker.TypeIdent(TypeTag.INT);
            if (typeName.equals(TypeName.BOOLEAN)) return maker.TypeIdent(TypeTag.BOOLEAN);
            if (typeName.equals(TypeName.BYTE)) return maker.TypeIdent(TypeTag.BYTE);
            if (typeName.equals(TypeName.SHORT)) return maker.TypeIdent(TypeTag.SHORT);
            if (typeName.equals(TypeName.LONG)) return maker.TypeIdent(TypeTag.LONG);
            if (typeName.equals(TypeName.CHAR)) return maker.TypeIdent(TypeTag.CHAR);
            if (typeName.equals(TypeName.FLOAT)) return maker.TypeIdent(TypeTag.FLOAT);
            if (typeName.equals(TypeName.DOUBLE)) return maker.TypeIdent(TypeTag.DOUBLE);
            if (typeName.equals(TypeName.VOID)) return maker.TypeIdent(TypeTag.VOID);
        }
        if (typeName instanceof ParameterizedTypeName ptn) {
            JCTree.JCExpression base = typeToExpression(ptn.rawType);
            List<JCTree.JCExpression> args = List.nil();
            for (TypeName arg : ptn.typeArguments) args = args.append(typeToExpression(arg));
            return maker.TypeApply(base, args);
        }
        if (typeName instanceof ClassName cn) {
            String full = cn.packageName() + "." + String.join(".", cn.simpleNames());
            String[] parts = full.split("\\.");
            JCTree.JCExpression expr = maker.Ident(names.fromString(parts[0]));
            for (int i = 1; i < parts.length; i++) expr = maker.Select(expr, names.fromString(parts[i]));
            return expr;
        }
        return maker.Ident(names.fromString(typeName.toString()));
    }

    private boolean methodExists(JCTree.JCClassDecl classDecl, JCTree.JCMethodDecl method) {
        for (JCTree def : classDecl.defs) {
            if (def instanceof JCTree.JCMethodDecl existing) {
                if (existing.name.equals(method.name) && existing.params.size() == method.params.size()) return true;
            }
        }
        return false;
    }

    private java.util.List<FieldInfo> extractFieldsFromInterface(TypeElement element) {
        java.util.List<FieldInfo> fields = new ArrayList<>();
        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) continue;
            ExecutableElement method = (ExecutableElement) enclosed;
            if (!method.getParameters().isEmpty() || method.getReturnType().toString().equals("void")) continue;
            String methodName = method.getSimpleName().toString();
            String name = methodName.startsWith("get") ? uncapitalize(methodName.substring(3)) : (methodName.startsWith("is") ? uncapitalize(methodName.substring(2)) : methodName);
            fields.add(new FieldInfo(name, TypeName.get(method.getReturnType()), methodName, "set" + capitalize(name)));
        }
        return fields;
    }

    private boolean isCancellable(TypeElement element) {
        for (TypeMirror iface : element.getInterfaces()) {
            if (iface.toString().startsWith("dev.sweety.event.api.CancellableEvent")) return true;
            Element ifaceElement = typeUtils.asElement(iface);
            if (ifaceElement instanceof TypeElement && isCancellable((TypeElement) ifaceElement)) return true;
        }
        return false;
    }

    private void writeJavaFile(String packageName, TypeSpec typeSpec) throws IOException {
        JavaFile.builder(packageName, typeSpec).build().writeTo(processingEnv.getFiler());
    }

    private static String capitalize(String name) { return (name == null || name.isEmpty()) ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1); }
    private static String uncapitalize(String name) { return (name == null || name.isEmpty()) ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1); }
    private static class FieldInfo {
        final String name;
        final TypeName type;
        final String getterName;
        final String setterName;
        FieldInfo(String name, TypeName type, String getterName, String setterName) { this.name = name; this.type = type; this.getterName = getterName; this.setterName = setterName; }
    }
}
