package dev.sweety.event.plugin;

import com.intellij.openapi.project.DumbService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EventAugmentProvider extends PsiAugmentProvider {

    private static final String GENERATE_EVENT = "dev.sweety.event.processor.GenerateEvent";

    @Override
    protected @NotNull <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                                       @NotNull Class<Psi> type,
                                                                       @Nullable String nameHint) {
        if (!(element instanceof PsiExtensibleClass psiClass)) return Collections.emptyList();
        if (DumbService.isDumb(psiClass.getProject())) return Collections.emptyList();
        if (!psiClass.isInterface()) return Collections.emptyList();
        if (!type.isAssignableFrom(PsiMethod.class)) return Collections.emptyList();
        if (nameHint != null && !nameHint.equals("of") && !nameHint.equals("ofMutable")) return Collections.emptyList();

        PsiAnnotation ann = findAnnotation(psiClass, GENERATE_EVENT);
        if (ann == null) return Collections.emptyList();

        String qName = psiClass.getQualifiedName();
        if (qName == null) return Collections.emptyList();

        String pkg = qName.contains(".") ? qName.substring(0, qName.lastIndexOf('.')) : "";
        String simpleName = psiClass.getName();
        if (simpleName == null) return Collections.emptyList();

        String cleanName = simpleName;
        if (cleanName.endsWith("Template")) cleanName = cleanName.substring(0, cleanName.length() - 8);
        else if (cleanName.endsWith("Def")) cleanName = cleanName.substring(0, cleanName.length() - 3);
        String eventName = cleanName.endsWith("Event") ? cleanName : cleanName + "Event";
        String mutableName = "Mutable" + eventName;

        boolean genImmutable = shouldGenerate(ann, false);
        boolean genMutable   = shouldGenerate(ann, true);

        List<PsiMethod> getters = new ArrayList<>();
        for (PsiMethod method : psiClass.getOwnMethods()) {
            if (method.getParameterList().getParametersCount() == 0
                    && !method.hasModifierProperty(PsiModifier.STATIC)
                    && !method.hasModifierProperty(PsiModifier.DEFAULT)) {
                getters.add(method);
            }
        }

        PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
        List<PsiMethod> result = new ArrayList<>(2);

        if (genImmutable && (nameHint == null || nameHint.equals("of"))) {
            // of(...) returns the event interface (== psiClass when no Template suffix)
            PsiClassType returnType = factory.createType(psiClass);
            result.add(buildFactory(psiClass, "of", returnType, getters));
        }

        if (genMutable && (nameHint == null || nameHint.equals("ofMutable"))) {
            // ofMutable(...) returns MutableXxxEvent — created as unresolved ref, resolved lazily
            String mutableFqn = pkg.isEmpty() ? mutableName : pkg + "." + mutableName;
            PsiClassType returnType = factory.createTypeByFQClassName(mutableFqn, psiClass.getResolveScope());
            result.add(buildFactory(psiClass, "ofMutable", returnType, getters));
        }

        //noinspection unchecked
        return (List<Psi>) result;
    }

    private static boolean shouldGenerate(PsiAnnotation ann, boolean mutable) {
        PsiAnnotationMemberValue val = ann.findAttributeValue("type");
        if (val == null) return true;
        String text = val.getText();
        String enumVal = text.contains(".") ? text.substring(text.lastIndexOf('.') + 1) : text;
        return mutable ? (enumVal.equals("MUTABLE") || enumVal.equals("BOTH"))
                       : (enumVal.equals("IMMUTABLE") || enumVal.equals("BOTH"));
    }

    private static LightMethodBuilder buildFactory(PsiExtensibleClass psiClass, String name,
                                                    PsiClassType returnType, List<PsiMethod> getters) {
        LightMethodBuilder m = new LightMethodBuilder(psiClass.getManager(), name);
        m.setContainingClass(psiClass);
        m.addModifier(PsiModifier.PUBLIC);
        m.addModifier(PsiModifier.STATIC);
        m.setMethodReturnType(returnType);
        for (PsiMethod getter : getters) {
            String paramName = getter.getName();
            if (paramName.startsWith("get") && paramName.length() > 3)
                paramName = Character.toLowerCase(paramName.charAt(3)) + paramName.substring(4);
            else if (paramName.startsWith("is") && paramName.length() > 2)
                paramName = Character.toLowerCase(paramName.charAt(2)) + paramName.substring(3);
            m.addParameter(paramName, getter.getReturnType());
        }
        return m;
    }

    private static boolean hasAnnotation(PsiClass psiClass, String fqn) {
        PsiModifierList ml = psiClass.getModifierList();
        if (ml == null) return false;
        for (PsiAnnotation ann : ml.getAnnotations()) {
            if (fqn.equals(ann.getQualifiedName())) return true;
        }
        return false;
    }

    private static @Nullable PsiAnnotation findAnnotation(PsiClass psiClass, String fqn) {
        PsiModifierList ml = psiClass.getModifierList();
        if (ml == null) return null;
        for (PsiAnnotation ann : ml.getAnnotations()) {
            if (fqn.equals(ann.getQualifiedName())) return ann;
        }
        return null;
    }
}
