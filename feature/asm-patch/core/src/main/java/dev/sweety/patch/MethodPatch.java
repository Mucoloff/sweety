package dev.sweety.patch;

import org.objectweb.asm.MethodVisitor;

import java.util.function.BiConsumer;

public final class MethodPatch {

    private final String methodName;
    private final String methodDesc;
    private final InjectionPoint injectionPoint;
    private final String targetOwner;
    private final String targetName;
    private final String targetDesc;
    private final BiConsumer<MethodVisitor, Integer> injector;

    public MethodPatch(String methodName, String methodDesc, InjectionPoint injectionPoint,
                       String targetOwner, String targetName, String targetDesc,
                       BiConsumer<MethodVisitor, Integer> injector) {
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        this.injectionPoint = injectionPoint;
        this.targetOwner = targetOwner;
        this.targetName = targetName;
        this.targetDesc = targetDesc;
        this.injector = injector;
    }

    public static MethodPatch atHead(String name, String desc, BiConsumer<MethodVisitor, Integer> injector) {
        return new MethodPatch(name, desc, InjectionPoint.HEAD, null, null, null, injector);
    }

    public static MethodPatch atReturn(String name, String desc, BiConsumer<MethodVisitor, Integer> injector) {
        return new MethodPatch(name, desc, InjectionPoint.RETURN, null, null, null, injector);
    }

    public static MethodPatch atTail(String name, String desc, BiConsumer<MethodVisitor, Integer> injector) {
        return atReturn(name, desc, injector);
    }

    public static MethodPatch atInvoke(String name, String desc, String targetOwner, String targetName, String targetDesc, BiConsumer<MethodVisitor, Integer> injector) {
        return new MethodPatch(name, desc, InjectionPoint.INVOKE, targetOwner, targetName, targetDesc, injector);
    }

    public static MethodPatch atField(String name, String desc, String targetOwner, String targetName, String targetDesc, BiConsumer<MethodVisitor, Integer> injector) {
        return new MethodPatch(name, desc, InjectionPoint.FIELD, targetOwner, targetName, targetDesc, injector);
    }

    public String methodName() { return methodName; }
    public String methodDesc() { return methodDesc; }
    public InjectionPoint injectionPoint() { return injectionPoint; }
    public String targetOwner() { return targetOwner; }
    public String targetName() { return targetName; }
    public String targetDesc() { return targetDesc; }
    public BiConsumer<MethodVisitor, Integer> injector() { return injector; }
}
