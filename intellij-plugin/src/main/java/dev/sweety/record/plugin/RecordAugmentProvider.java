package dev.sweety.record.plugin;

import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightMethodBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecordAugmentProvider extends PsiAugmentProvider {

    private static final String RECORD_DATA = "dev.sweety.record.annotations.RecordData";
    private static final String RECORD_GETTER = "dev.sweety.record.annotations.RecordGetter";
    private static final String SETTER = "dev.sweety.record.annotations.Setter";
    private static final String ALL_ARGS_CONSTRUCTOR = "dev.sweety.record.annotations.AllArgsConstructor";
    private static final String DATA_IGNORE = "dev.sweety.record.annotations.DataIgnore";

    @Override
    protected @NotNull <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                                      @NotNull Class<Psi> type) {
        if (!type.equals(PsiMethod.class) || !(element instanceof PsiClass psiClass)) {
            return Collections.emptyList();
        }

        if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
            return Collections.emptyList();
        }

        List<Psi> methods = new ArrayList<>();

        PsiAnnotation dataAnn = psiClass.getAnnotation(RECORD_DATA);
        PsiAnnotation getterAnn = psiClass.getAnnotation(RECORD_GETTER);
        PsiAnnotation setterAnn = psiClass.getAnnotation(SETTER);
        PsiAnnotation allArgsConstructorAnn = psiClass.getAnnotation(ALL_ARGS_CONSTRUCTOR);

        boolean classHasData = dataAnn != null;
        boolean classHasGetter = getterAnn != null;
        boolean classHasSetter = setterAnn != null;
        boolean classHasAllArgsConstructor = allArgsConstructorAnn != null;

        boolean applyAll = true;
        boolean includeStatic = false;

        List<String> setterTypes = new ArrayList<>();

        if (classHasData) {
            applyAll = getBooleanAttribute(dataAnn, "applyAll", true);
            includeStatic = getBooleanAttribute(dataAnn, "includeStatic", false);
            setterTypes = getEnumArrayAttribute(dataAnn, "setterTypes");
            if (setterTypes.isEmpty()) setterTypes.add("DEFAULT");
            if (getBooleanAttribute(dataAnn, "allArgsConstructor", false)) {
                classHasAllArgsConstructor = true;
            }
        } else if (classHasGetter) {
            applyAll = getBooleanAttribute(getterAnn, "applyAll", true);
            includeStatic = getBooleanAttribute(getterAnn, "includeStatic", false);
        } else if (classHasSetter) {
            applyAll = getBooleanAttribute(setterAnn, "applyAll", true);
            includeStatic = getBooleanAttribute(setterAnn, "includeStatic", false);
            setterTypes = getEnumArrayAttribute(setterAnn, "types");
            if (setterTypes.isEmpty()) setterTypes.add("DEFAULT");
        } else {
            // Default se nessuna annotazione principale è presente ma magari c'è AllArgsConstructor
            setterTypes.add("DEFAULT");
        }

        if (classHasAllArgsConstructor) {
            methods.add((Psi) createAllArgsConstructor(psiClass));
        }

        for (PsiField field : psiClass.getFields()) {
            if (field.getAnnotation(DATA_IGNORE) != null) continue;

            boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
            boolean isFinal = field.hasModifierProperty(PsiModifier.FINAL);

            boolean fieldHasData = field.getAnnotation(RECORD_DATA) != null;
            boolean fieldHasGetter = field.getAnnotation(RECORD_GETTER) != null;
            boolean fieldHasSetter = field.getAnnotation(SETTER) != null;

            boolean shouldGenGetter = fieldHasData || fieldHasGetter;
            boolean shouldGenSetter = fieldHasData || fieldHasSetter;

            boolean doSearchGetters = classHasData || classHasGetter;
            boolean doSearchSetters = classHasData || classHasSetter;

            if (!shouldGenGetter && doSearchGetters && applyAll) {
                if (!isStatic || includeStatic) shouldGenGetter = true;
            }

            if (!shouldGenSetter && doSearchSetters && applyAll) {
                if (!isStatic || includeStatic) shouldGenSetter = true;
            }

            if (shouldGenGetter) {
                methods.add((Psi) createGetter(field, psiClass));
            }

            if (shouldGenSetter && !isFinal) {
                for (String sType : setterTypes) {
                    boolean classic = false;
                    boolean builder = false;
                    switch (sType) {
                        case "DEFAULT":
                            classic = true;
                            break;
                        case "FLUENT":
                            break;
                        case "BUILDER":
                            classic = true;
                            builder = true;
                            break;
                        case "BUILDER_FLUENT":
                            builder = true;
                            break;
                    }
                    methods.add((Psi) createSetter(field, psiClass, classic, builder));
                }
            }
        }

        return methods;
    }

    private boolean getBooleanAttribute(PsiAnnotation annotation, String attributeName, boolean defaultValue) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue(attributeName);
        if (value == null) return defaultValue;
        String text = value.getText();
        if ("true".equals(text)) return true;
        if ("false".equals(text)) return false;
        return defaultValue;
    }

    private List<String> getEnumArrayAttribute(PsiAnnotation annotation, String attributeName) {
        List<String> results = new ArrayList<>();
        PsiAnnotationMemberValue value = annotation.findAttributeValue(attributeName);
        if (value instanceof PsiArrayInitializerMemberValue array) {
            for (PsiAnnotationMemberValue item : array.getInitializers()) {
                results.add(resolveEnumName(item));
            }
        } else if (value != null) {
            results.add(resolveEnumName(value));
        }
        return results;
    }

    private String resolveEnumName(PsiAnnotationMemberValue value) {
        if (value instanceof PsiReferenceExpression ref) {
            return ref.getReferenceName();
        }
        return "";
    }

    private PsiMethod createGetter(PsiField field, PsiClass psiClass) {
        String fieldName = field.getName();
        LightMethodBuilder method = new LightMethodBuilder(psiClass.getManager(), field.getName());
        method.setMethodReturnType(field.getType());
        method.setContainingClass(psiClass);
        method.addModifier(PsiModifier.PUBLIC);
        if (field.hasModifierProperty(PsiModifier.STATIC)) {
            method.addModifier(PsiModifier.STATIC);
        }
        method.setNavigationElement(field); // Navigate to field when clicking method
        return method;
    }

    private PsiMethod createSetter(PsiField field, PsiClass psiClass, boolean classic, boolean builder) {
        String fieldName = field.getName();
        String methodName;

        if (classic) {
            methodName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        } else {
            methodName = fieldName;
        }

        LightMethodBuilder method = new LightMethodBuilder(psiClass.getManager(), methodName);

        if (builder && !field.hasModifierProperty(PsiModifier.STATIC)) {
             method.setMethodReturnType(JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass));
        } else {
             method.setMethodReturnType(PsiTypes.voidType());
        }

        method.setContainingClass(psiClass);
        method.addModifier(PsiModifier.PUBLIC);
        if (field.hasModifierProperty(PsiModifier.STATIC)) {
            method.addModifier(PsiModifier.STATIC);
        }
        method.addParameter(fieldName, field.getType());
        method.setNavigationElement(field);
        return method;
    }

    private PsiMethod createAllArgsConstructor(PsiClass psiClass) {
        LightMethodBuilder method = new LightMethodBuilder(psiClass.getManager(), psiClass.getName());
        method.setConstructor(true);
        method.setContainingClass(psiClass);
        method.addModifier(PsiModifier.PUBLIC);

        for (PsiField field : psiClass.getFields()) {
            // Ignora campi statici o annotati con DataIgnore
            if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
            if (field.getAnnotation(DATA_IGNORE) != null) continue;

            method.addParameter(field.getName(), field.getType());
        }

        method.setNavigationElement(psiClass);
        return method;
    }
}
