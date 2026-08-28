package dev.sweety.packet.plugin;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
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

/**
 * Provides a virtual static {@code create(...)} factory method on interfaces
 * annotated with {@code @BuildPacket}. The generated method mirrors the
 * write-constructor that {@code PacketProcessor} emits for the concrete
 * {@code <Interface>Packet} class so that call-sites resolve without errors.
 */
public class PacketAugmentProvider extends PsiAugmentProvider {

    private static final String BUILD_PACKET = "dev.sweety.packet.processor.BuildPacket";

    @Override
    protected @NotNull <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                                       @NotNull Class<Psi> type,
                                                                       @Nullable String nameHint) {
        if (!(element instanceof PsiExtensibleClass psiClass)) return Collections.emptyList();
        if (DumbService.isDumb(psiClass.getProject())) return Collections.emptyList();
        if (!psiClass.isInterface()) return Collections.emptyList();
        if (!type.isAssignableFrom(PsiMethod.class)) return Collections.emptyList();

        PsiAnnotation ann = findAnnotation(psiClass, BUILD_PACKET);
        if (ann == null) return Collections.emptyList();

        if (nameHint != null && !nameHint.equals("create")) {
            return Collections.emptyList();
        }

        List<PsiMethod> result = new ArrayList<>();
        addCreateMethod(psiClass, ann, result);
        //noinspection unchecked
        return (List<Psi>) result;
    }

    private void addCreateMethod(PsiExtensibleClass psiClass, PsiAnnotation ann, List<PsiMethod> result) {
        String qName = psiClass.getQualifiedName();
        if (qName == null) return;

        // Resolve generated class name: annotation.name() or "{Interface}Packet"
        String customName = getStringAttribute(ann, "name", "");
        String packetSimpleName = customName.isEmpty() ? (psiClass.getName() + "Packet") : customName;

        // Resolve generated package: original + annotation.path() (default ".packet")
        String path = getStringAttribute(ann, "path", ".packet");
        String originalPackage = StringUtil.getPackageName(qName);
        String packetPackage = originalPackage + path;
        String packetFqn = packetPackage + "." + packetSimpleName;

        // Collect write-constructor parameters: all zero-arg interface methods, in declaration order
        List<PsiMethod> getters = new ArrayList<>();
        for (PsiMethod method : psiClass.getOwnMethods()) {
            if (method.getParameterList().getParametersCount() == 0
                    && !method.hasModifierProperty(PsiModifier.STATIC)) {
                getters.add(method);
            }
        }

        LightMethodBuilder create = new LightMethodBuilder(psiClass.getManager(), "create");
        create.setContainingClass(psiClass);
        create.addModifier(PsiModifier.PUBLIC);
        create.addModifier(PsiModifier.STATIC);
        create.setNavigationElement(ann);

        create.setMethodReturnType(JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass));

        for (PsiMethod getter : getters) {
            // Respect @BuildPacket.name() on the method to use as parameter name
            PsiAnnotation fieldAnn = findAnnotation(getter, BUILD_PACKET);
            String paramName = fieldAnn != null ? getStringAttribute(fieldAnn, "name", "") : "";
            if (paramName.isEmpty()) paramName = getter.getName();
            create.addParameter(paramName, getter.getReturnType());
        }

        result.add(create);
    }

    private @Nullable PsiAnnotation findAnnotation(@NotNull PsiClass psiClass, @NotNull String fqn) {
        PsiModifierList ml = psiClass.getModifierList();
        if (ml == null) return null;
        for (PsiAnnotation a : ml.getAnnotations()) {
            if (a.hasQualifiedName(fqn)) return a;
        }
        return null;
    }

    private @Nullable PsiAnnotation findAnnotation(@NotNull PsiMethod method, @NotNull String fqn) {
        PsiModifierList ml = method.getModifierList();
        for (PsiAnnotation a : ml.getAnnotations()) {
            if (a.hasQualifiedName(fqn)) return a;
        }
        return null;
    }

    private String getStringAttribute(@NotNull PsiAnnotation annotation, @NotNull String attr, @NotNull String def) {
        PsiAnnotationMemberValue val = annotation.findAttributeValue(attr);
        if (val instanceof PsiLiteralExpression lit) {
            Object o = lit.getValue();
            if (o instanceof String s) return s;
        }
        return def;
    }
}
