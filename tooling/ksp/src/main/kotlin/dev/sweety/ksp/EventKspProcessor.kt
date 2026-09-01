package dev.sweety.ksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.javapoet.*
import dev.sweety.event.api.*
import org.jetbrains.annotations.NotNull
import javax.lang.model.element.Modifier

private const val GENERATE_EVENT    = "dev.sweety.event.processor.GenerateEvent"
private const val CANCELLABLE_EVENT = "dev.sweety.event.api.CancellableEvent"

class EventKspProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        resolver.getSymbolsWithAnnotation(GENERATE_EVENT).forEach { sym ->
            if (sym is KSClassDeclaration && sym.classKind == ClassKind.INTERFACE) {
                if (sym.validate()) generateEvent(sym)
                else deferred += sym
            }
        }
        return deferred
    }

    private fun generateEvent(decl: KSClassDeclaration) {
        val ann          = decl.annotation(GENERATE_EVENT)
        val pkg          = decl.packageName.asString()
        val templateName = decl.simpleName.asString()

        val customValue  = ann?.arg<String>("value") ?: ""
        val genImmutable = ann?.arg<Boolean>("immutable") ?: true
        val genMutable   = ann?.arg<Boolean>("mutable")   ?: true

        val baseName = if (customValue.isNotEmpty()) customValue else templateName
        val cleanName = when {
            baseName.endsWith("Template") -> baseName.dropLast(8)
            baseName.endsWith("Def")      -> baseName.dropLast(3)
            else                          -> baseName
        }
        val eventName        = if (cleanName.endsWith("Event")) cleanName else "${cleanName}Event"
        val mutableName      = "Mutable$eventName"
        val immutableImplName = "${eventName}Impl"
        val mutableImplName  = "Mutable${eventName}Impl"
        val factoryName      = "${eventName}Factory"

        val isTemplateTheInterface = templateName == eventName
        val cancellable = decl.isCancellable()
        val fields = decl.extractFields()

        val eventClass   = ClassName.get(pkg, eventName)
        val mutableClass = ClassName.get(pkg, mutableName)

        // 1. Immutable event interface (only when template name ≠ event name)
        if (!isTemplateTheInterface && genImmutable) {
            val superIface = if (cancellable)
                ParameterizedTypeName.get(ClassName.get(CancellableEvent::class.java), eventClass)
            else
                ParameterizedTypeName.get(ClassName.get(Event::class.java), eventClass)
            val iface = TypeSpec.interfaceBuilder(eventName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(superIface)
            fields.forEach { f ->
                iface.addMethod(
                    MethodSpec.methodBuilder(f.getterName)
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(f.typeName)
                        .build()
                )
            }
            writeFile(pkg, iface.build(), decl)
        }

        // 2. Mutable interface
        if (genMutable) {
            val mutableIface = TypeSpec.interfaceBuilder(mutableName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(eventClass)
                .addSuperinterface(ParameterizedTypeName.get(ClassName.get(MutableEvent::class.java), eventClass))
            fields.forEach { f ->
                mutableIface.addMethod(
                    MethodSpec.methodBuilder(f.setterName)
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .addParameter(f.typeName, f.name)
                        .returns(TypeName.VOID)
                        .build()
                )
            }
            mutableIface.addMethod(
                MethodSpec.methodBuilder("post")
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .addAnnotation(Override::class.java)
                    .addAnnotation(NotNull::class.java)
                    .returns(mutableClass)
                    .build()
            )
            writeFile(pkg, mutableIface.build(), decl)
        }

        // 3. Immutable impl
        if (genImmutable) {
            val superClass = if (cancellable)
                ParameterizedTypeName.get(ClassName.get(AbstractCancellableEvent::class.java), eventClass)
            else
                ParameterizedTypeName.get(ClassName.get(AbstractEvent::class.java), eventClass)
            val impl = TypeSpec.classBuilder(immutableImplName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(superClass)
                .addSuperinterface(eventClass)
            fields.forEach { f ->
                impl.addField(FieldSpec.builder(f.typeName, f.name, Modifier.PRIVATE, Modifier.FINAL).build())
            }
            // package-private constructor
            val ctor = MethodSpec.constructorBuilder()
            fields.forEach { f -> ctor.addParameter(f.typeName, f.name) }
            fields.forEach { f -> ctor.addStatement("this.\$N = \$N", f.name, f.name) }
            impl.addMethod(ctor.build())
            // getters
            fields.forEach { f ->
                impl.addMethod(
                    MethodSpec.methodBuilder(f.getterName)
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override::class.java)
                        .returns(f.typeName)
                        .addStatement("return this.\$N", f.name)
                        .build()
                )
            }
            writeFile(pkg, impl.build(), decl)
        }

        // 4. Mutable impl
        if (genMutable) {
            val superClass = if (cancellable)
                ParameterizedTypeName.get(ClassName.get(AbstractCancellableEvent::class.java), eventClass)
            else
                ParameterizedTypeName.get(ClassName.get(AbstractEvent::class.java), eventClass)
            val impl = TypeSpec.classBuilder(mutableImplName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(superClass)
                .addSuperinterface(mutableClass)
            fields.forEach { f ->
                impl.addField(FieldSpec.builder(f.typeName, f.name, Modifier.PRIVATE).build())
            }
            // package-private constructor
            val ctor = MethodSpec.constructorBuilder()
            fields.forEach { f -> ctor.addParameter(f.typeName, f.name) }
            fields.forEach { f -> ctor.addStatement("this.\$N = \$N", f.name, f.name) }
            impl.addMethod(ctor.build())
            // getters
            fields.forEach { f ->
                impl.addMethod(
                    MethodSpec.methodBuilder(f.getterName)
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override::class.java)
                        .returns(f.typeName)
                        .addStatement("return this.\$N", f.name)
                        .build()
                )
            }
            // setters
            fields.forEach { f ->
                impl.addMethod(
                    MethodSpec.methodBuilder(f.setterName)
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(Override::class.java)
                        .addParameter(f.typeName, f.name)
                        .addStatement("this.\$N = \$N", f.name, f.name)
                        .build()
                )
            }
            // post()
            impl.addMethod(
                MethodSpec.methodBuilder("post")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override::class.java)
                    .addAnnotation(NotNull::class.java)
                    .returns(mutableClass)
                    .addStatement("this.pre = false")
                    .addStatement("return this")
                    .build()
            )
            // toImmutable() — delegates to factory
            if (genImmutable) {
                val factoryClass = ClassName.get(pkg, factoryName)
                val toImmutable = MethodSpec.methodBuilder("toImmutable")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override::class.java)
                    .addAnnotation(NotNull::class.java)
                    .returns(eventClass)
                val args = fields.joinToString(", ") { "this.${it.name}" }
                toImmutable.addStatement("return \$T.of($args)", factoryClass)
                impl.addMethod(toImmutable.build())
            }
            writeFile(pkg, impl.build(), decl)
        }

        // 5. Factory
        val factory = TypeSpec.classBuilder(factoryName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
        val implImmutableClass = ClassName.get(pkg, immutableImplName)
        val implMutableClass   = ClassName.get(pkg, mutableImplName)

        if (genImmutable) {
            val of = MethodSpec.methodBuilder("of")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(eventClass)
            fields.forEach { f -> of.addParameter(f.typeName, f.name) }
            val args = fields.joinToString(", ") { it.name }
            of.addStatement("return new \$T($args)", implImmutableClass)
            factory.addMethod(of.build())
        }
        if (genMutable) {
            val ofMutable = MethodSpec.methodBuilder("ofMutable")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(mutableClass)
            fields.forEach { f -> ofMutable.addParameter(f.typeName, f.name) }
            val args = fields.joinToString(", ") { it.name }
            ofMutable.addStatement("return new \$T($args)", implMutableClass)
            factory.addMethod(ofMutable.build())
        }
        writeFile(pkg, factory.build(), decl)
    }

    private fun writeFile(pkg: String, typeSpec: TypeSpec, decl: KSClassDeclaration) {
        val file = codeGenerator.createNewFile(
            Dependencies(false, decl.containingFile!!), pkg, typeSpec.name!!, "java"
        )
        file.bufferedWriter().use { JavaFile.builder(pkg, typeSpec).build().writeTo(it) }
    }
}

// ── Field helpers ─────────────────────────────────────────────────────────────

private data class FieldInfo(
    val name: String,
    val typeName: TypeName,
    val getterName: String,
    val setterName: String,
)

private fun KSClassDeclaration.extractFields(): List<FieldInfo> =
    getDeclaredFunctions()
        .filter { func ->
            func.isAbstract &&
            func.parameters.isEmpty() &&
            func.annotations.none { it.shortName.asString() == "Ignore" } &&
            func.returnType?.resolve()?.declaration?.qualifiedName?.asString() != "kotlin.Unit"
        }
        .map { func ->
            val methodName = func.simpleName.asString()
            val name = when {
                methodName.startsWith("get") -> uncapitalize(methodName.removePrefix("get"))
                methodName.startsWith("is")  -> uncapitalize(methodName.removePrefix("is"))
                else                          -> methodName
            }
            // Mirror APT EventFieldScanner: simple string replace, not prefix-only
            val setter = methodName.replace("is", "set").replace("get", "set")
            val typeName = func.returnType!!.resolve().toJavaPoet()
            FieldInfo(name, typeName, methodName, setter)
        }
        .toList()

private fun KSClassDeclaration.isCancellable(): Boolean =
    superTypes.any { superRef ->
        val qName = superRef.resolve().declaration.qualifiedName?.asString() ?: return@any false
        qName.startsWith(CANCELLABLE_EVENT)
    }

private fun uncapitalize(s: String) =
    if (s.isEmpty()) s else s[0].lowercaseChar() + s.substring(1)
