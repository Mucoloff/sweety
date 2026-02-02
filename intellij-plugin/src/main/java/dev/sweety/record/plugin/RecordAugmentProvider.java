package dev.sweety.record.plugin;

import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightMethodBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecordAugmentProvider extends PsiAugmentProvider {

    private static final String RECORD_DATA = "dev.sweety.record.annotations.RecordData";
    private static final String RECORD_GETTER = "dev.sweety.record.annotations.RecordGetter";
    private static final String SETTER = "dev.sweety.record.annotations.Setter";
    private static final String ALL_ARGS_CONSTRUCTOR = "dev.sweety.record.annotations.AllArgsConstructor";
    private static final String UTILITY_CLASS = "dev.sweety.record.annotations.UtilityClass";
    private static final String DATA_IGNORE = "dev.sweety.record.annotations.DataIgnore";

    @Override
    @SuppressWarnings("unchecked")
    protected @NotNull <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                                      @NotNull Class<Psi> type,
                                                                      @Nullable String nameHint) {
        if (!(element instanceof PsiClass psiClass)) {
            return Collections.emptyList();
        }

        if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
            return Collections.emptyList();
        }

        List<Psi> result = new ArrayList<>();

        boolean classHasData = false;
        boolean classHasGetter = false;
        boolean classHasSetter = false;
        boolean classHasAllArgsConstructor = false;
        boolean classHasUtilityClass = false;

        PsiAnnotation dataAnn = null;
        PsiAnnotation getterAnn = null;
        PsiAnnotation setterAnn = null;
        PsiAnnotation allArgsConstructorAnn = null;
        PsiAnnotation utilityClassAnn = null;

        PsiModifierList modifierList = psiClass.getModifierList();
        if (modifierList != null) {
            for (PsiAnnotation ann : modifierList.getAnnotations()) {
                PsiJavaCodeReferenceElement ref = ann.getNameReferenceElement();
                if (ref == null) continue;
                String refName = ref.getReferenceName();
                if (refName == null) continue;

                if ("RecordData".equals(refName) || RECORD_DATA.endsWith("." + refName)) {
                    if (ann.hasQualifiedName(RECORD_DATA)) {
                        dataAnn = ann;
                        classHasData = true;
                    }
                } else if ("RecordGetter".equals(refName) || RECORD_GETTER.endsWith("." + refName)) {
                    if (ann.hasQualifiedName(RECORD_GETTER)) {
                        getterAnn = ann;
                        classHasGetter = true;
                    }
                } else if ("Setter".equals(refName) || SETTER.endsWith("." + refName)) {
                    if (ann.hasQualifiedName(SETTER)) {
                        setterAnn = ann;
                        classHasSetter = true;
                    }
                } else if ("AllArgsConstructor".equals(refName) || ALL_ARGS_CONSTRUCTOR.endsWith("." + refName)) {
                    if (ann.hasQualifiedName(ALL_ARGS_CONSTRUCTOR)) {
                        allArgsConstructorAnn = ann;
                        classHasAllArgsConstructor = true;
                    }
                } else if ("UtilityClass".equals(refName) || UTILITY_CLASS.endsWith("." + refName)) {
                    if (ann.hasQualifiedName(UTILITY_CLASS)) {
                        utilityClassAnn = ann;
                        classHasUtilityClass = true;
                    }
                }
            }
        }

        if (type.isAssignableFrom(PsiMethod.class)) {
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
                setterTypes.add("DEFAULT");
            }

            if (classHasAllArgsConstructor) {
                result.add((Psi) createAllArgsConstructor(psiClass));
            }

            if (classHasUtilityClass) {
                //result.add((Psi) createPrivateConstructor(psiClass));
                for (PsiMethod method : getOwnMethods(psiClass)) {
                    if (method.isConstructor()) continue;
                    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
                        //result.remove(method);
                        //method.delete();
                        //result.add((Psi) createStaticDelegate(method, psiClass));
                        //method.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
                        //nessuno di questi funziona senza errori
                    }
                }
            }

            for (PsiField field : getOwnFields(psiClass)) {
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
                    result.add((Psi) createGetter(field, psiClass));
                }

                if (shouldGenSetter && !isFinal) {
                    for (String sType : setterTypes) {
                        final boolean classic;
                        final boolean builder = switch (sType) {
                            case "FLUENT" -> {
                                classic = false;
                                yield false;
                            }
                            case "BUILDER" -> {
                                classic = true;
                                yield true;
                            }
                            case "BUILDER_FLUENT" -> {
                                classic = false;
                                yield true;
                            }
                            default -> {
                                classic = true;
                                yield false;
                            }
                        };
                        result.add((Psi) createSetter(field, psiClass, classic, builder));
                    }
                }
            }
        }

        if (type.isAssignableFrom(PsiField.class) && classHasUtilityClass) {
             for (PsiField field : getOwnFields(psiClass)) {
                 if (!field.hasModifierProperty(PsiModifier.STATIC)) {
                     result.add((Psi) createStaticFieldDelegate(field, psiClass));
                 }
             }
        }

        return result;
    }

    private List<PsiField> getOwnFields(PsiClass psiClass) {
        List<PsiField> fields = new ArrayList<>();
        for (PsiElement child : psiClass.getChildren()) {
            if (child instanceof PsiField field) {
                fields.add(field);
            }
        }
        return fields;
    }

    private List<PsiMethod> getOwnMethods(PsiClass psiClass) {
        List<PsiMethod> methods = new ArrayList<>();
        for (PsiElement child : psiClass.getChildren()) {
            if (child instanceof PsiMethod method) {
                methods.add(method);
            }
        }
        return methods;
    }

    private PsiField createStaticFieldDelegate(PsiField field, PsiClass psiClass) {
        LightFieldBuilder builder = new LightFieldBuilder(field.getName(), field.getType(), field);
        builder.setContainingClass(psiClass);
        PsiModifierList modifierList = field.getModifierList();

        List<String> modifiers = new ArrayList<>();
        if (modifierList != null) {
            for (String modifier : PsiModifier.MODIFIERS) {
                if (modifierList.hasModifierProperty(modifier) && !modifier.equals(PsiModifier.STATIC)) {
                    modifiers.add(modifier);
                }
            }
        }
        modifiers.add(PsiModifier.STATIC);
        builder.setModifiers(modifiers.toArray(new String[0]));

        builder.setNavigationElement(field);
        return builder;
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

        for (PsiField field : getOwnFields(psiClass)) {
            // Ignora campi statici o annotati con DataIgnore
            if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
            if (field.getAnnotation(DATA_IGNORE) != null) continue;

            method.addParameter(field.getName(), field.getType());
        }

        method.setNavigationElement(psiClass);
        return method;
    }

    private PsiMethod createPrivateConstructor(PsiClass psiClass) {
        LightMethodBuilder method = new LightMethodBuilder(psiClass.getManager(), psiClass.getName());
        method.setConstructor(true);
        method.setContainingClass(psiClass);
        method.addModifier(PsiModifier.PRIVATE);
        method.setNavigationElement(psiClass);
        return method;
    }

    private PsiMethod createStaticDelegate(PsiMethod method, PsiClass psiClass) {
        LightMethodBuilder builder = new LightMethodBuilder(psiClass.getManager(), method.getName());
        builder.setMethodReturnType(method.getReturnType());
        builder.setContainingClass(psiClass);
        builder.addModifier(PsiModifier.PUBLIC);
        builder.addModifier(PsiModifier.STATIC);
        builder.setNavigationElement(method);

        for (PsiParameter p : method.getParameterList().getParameters()) {
            builder.addParameter(p.getName(), p.getType());
        }

        for (PsiClassType type : method.getThrowsList().getReferencedTypes()) {
            builder.addException(type);
        }

        return builder;
    }
}
