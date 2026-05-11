package dev.sweety.event.plugin;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightMethodBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Provides virtual methods for @GenerateEvent annotated interfaces.
 */
public class EventAugmentProvider extends PsiAugmentProvider {

    private static final String GENERATE_EVENT = "dev.sweety.event.processor.GenerateEvent";

    @Override
    protected @NotNull <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                                       @NotNull Class<Psi> type,
                                                                       @Nullable String nameHint) {
        if (!(element instanceof PsiClass psiClass)) {
            return Collections.emptyList();
        }

        if (!psiClass.isInterface()) {
            return Collections.emptyList();
        }

        if (!type.isAssignableFrom(PsiMethod.class)) {
            return Collections.emptyList();
        }

        PsiAnnotation eventAnn = findAnnotation(psiClass, GENERATE_EVENT);
        if (eventAnn == null) {
            return Collections.emptyList();
        }

        List<PsiMethod> result = new ArrayList<>();
        addEventMethods(psiClass, eventAnn, result);
        
        //noinspection unchecked
        return (List<Psi>) result;
    }

    private void addEventMethods(PsiClass psiClass, PsiAnnotation ann, List<PsiMethod> result) {
        boolean genImmutable = getBooleanAttribute(ann, "immutable", true);
        boolean genMutable = getBooleanAttribute(ann, "mutable", true);
        String customValue = getStringAttribute(ann, "value", "");

        String qName = psiClass.getQualifiedName();
        if (qName == null) return;
        
        String packageName = StringUtil.getPackageName(qName);
        String baseName = customValue.isEmpty() ? psiClass.getName() : customValue;
        if (baseName == null) return;

        String cleanName = baseName;
        if (cleanName.endsWith("Template")) cleanName = cleanName.substring(0, cleanName.length() - 8);
        else if (cleanName.endsWith("Def")) cleanName = cleanName.substring(0, cleanName.length() - 3);

        String eventName = cleanName.endsWith("Event") ? cleanName : cleanName + "Event";
        String mutableName = "Mutable" + eventName;

        // Collect fields from interface methods
        List<PsiMethod> getters = new ArrayList<>();
        for (PsiMethod method : psiClass.getMethods()) {
            if (method.getParameterList().getParametersCount() == 0 && !method.hasModifierProperty(PsiModifier.STATIC)) {
                getters.add(method);
            }
        }

        if (genImmutable) {
            LightMethodBuilder of = new LightMethodBuilder(psiClass.getManager(), "of");
            of.setContainingClass(psiClass);
            of.addModifier(PsiModifier.PUBLIC);
            of.addModifier(PsiModifier.STATIC);
            of.setNavigationElement(ann);
            
            PsiClass eventType = JavaPsiFacade.getInstance(psiClass.getProject()).findClass(packageName + "." + eventName, psiClass.getResolveScope());
            if (eventType != null) of.setMethodReturnType(JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(eventType));
            else of.setMethodReturnType(JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass));

            for (PsiMethod getter : getters) {
                String name = getter.getName();
                if (name.startsWith("get")) name = StringUtil.decapitalize(name.substring(3));
                else if (name.startsWith("is")) name = StringUtil.decapitalize(name.substring(2));
                of.addParameter(name, getter.getReturnType());
            }
            result.add(of);
        }

        if (genMutable) {
            LightMethodBuilder ofMutable = new LightMethodBuilder(psiClass.getManager(), "ofMutable");
            ofMutable.setContainingClass(psiClass);
            ofMutable.addModifier(PsiModifier.PUBLIC);
            ofMutable.addModifier(PsiModifier.STATIC);
            ofMutable.setNavigationElement(ann);

            PsiClass mutableType = JavaPsiFacade.getInstance(psiClass.getProject()).findClass(packageName + "." + mutableName, psiClass.getResolveScope());
            if (mutableType != null) ofMutable.setMethodReturnType(JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(mutableType));
            else ofMutable.setMethodReturnType(JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass));

            for (PsiMethod getter : getters) {
                String name = getter.getName();
                if (name.startsWith("get")) name = StringUtil.decapitalize(name.substring(3));
                else if (name.startsWith("is")) name = StringUtil.decapitalize(name.substring(2));
                ofMutable.addParameter(name, getter.getReturnType());
            }
            result.add(ofMutable);
        }
    }

    private PsiAnnotation findAnnotation(PsiClass psiClass, String qualifiedName) {
        PsiModifierList modifierList = psiClass.getModifierList();
        if (modifierList == null) return null;
        for (PsiAnnotation ann : modifierList.getAnnotations()) {
            if (ann.hasQualifiedName(qualifiedName)) return ann;
        }
        return null;
    }

    private String getStringAttribute(PsiAnnotation annotation, String attributeName, String defaultValue) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue(attributeName);
        if (value instanceof PsiLiteralExpression literal) {
            Object o = literal.getValue();
            if (o instanceof String s) return s;
        }
        return defaultValue;
    }

    private boolean getBooleanAttribute(PsiAnnotation annotation, String attributeName, boolean defaultValue) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue(attributeName);
        if (value == null) return defaultValue;
        String text = value.getText();
        if ("true".equals(text)) return true;
        if ("false".equals(text)) return false;
        return defaultValue;
    }
}
