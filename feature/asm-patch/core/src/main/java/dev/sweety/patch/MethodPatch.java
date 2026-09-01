package dev.sweety.patch;

import org.objectweb.asm.MethodVisitor;

import java.util.function.BiConsumer;

public final class MethodPatch {

    private final String methodName;
    private final String methodDesc;
    private final InjectionPoint injectionPoint;
    private final BiConsumer<MethodVisitor, Integer> injector;

    public MethodPatch(String methodName, String methodDesc, InjectionPoint injectionPoint, BiConsumer<MethodVisitor, Integer> injector) {
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        this.injectionPoint = injectionPoint;
        this.injector = injector;
    }

    public static MethodPatch atHead(String name, String desc, BiConsumer<MethodVisitor, Integer> injector) {
        return new MethodPatch(name, desc, InjectionPoint.HEAD, injector);
    }

    public static MethodPatch atReturn(String name, String desc, BiConsumer<MethodVisitor, Integer> injector) {
        return new MethodPatch(name, desc, InjectionPoint.RETURN, injector);
    }

    public String methodName() { return methodName; }
    public String methodDesc() { return methodDesc; }
    public InjectionPoint injectionPoint() { return injectionPoint; }
    public BiConsumer<MethodVisitor, Integer> injector() { return injector; }
}
