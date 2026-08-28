package dev.sweety.event.processor;

import com.squareup.javapoet.TypeName;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;

/** Scans interface elements for field descriptors and cancellability. No JDK-internal APIs used. */
public class EventFieldScanner {

    private final Types typeUtils;

    EventFieldScanner(Types typeUtils) {
        this.typeUtils = typeUtils;
    }

    List<FieldInfo> extractFields(TypeElement element) {
        List<FieldInfo> fields = new ArrayList<>();
        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) continue;
            ExecutableElement method = (ExecutableElement) enclosed;
            if (method.isDefault() ||method.getModifiers().contains(Modifier.DEFAULT)) continue;
            if (!method.getParameters().isEmpty() || method.getReturnType().toString().equals("void")) continue;
            String methodName = method.getSimpleName().toString();
            String name = methodName.startsWith("get") ? uncapitalize(methodName.substring(3))
                    : (methodName.startsWith("is") ? uncapitalize(methodName.substring(2)) : methodName);
            fields.add(new FieldInfo(name, TypeName.get(method.getReturnType()), methodName,
                    methodName.replace("is", "set").replace("get", "set")));
        }
        return fields;
    }

    boolean isCancellable(TypeElement element) {
        for (TypeMirror iface : element.getInterfaces()) {
            if (iface.toString().startsWith("dev.sweety.event.api.CancellableEvent")) return true;
            Element ifaceElement = typeUtils.asElement(iface);
            if (ifaceElement instanceof TypeElement te && isCancellable(te)) return true;
        }
        return false;
    }

    private static String uncapitalize(String name) {
        return (name == null || name.isEmpty()) ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    public record FieldInfo(String name, TypeName type, String getterName, String setterName) {}
}
