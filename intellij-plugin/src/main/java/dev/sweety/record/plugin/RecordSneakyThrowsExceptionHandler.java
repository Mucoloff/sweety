package dev.sweety.record.plugin;

import com.intellij.codeInsight.CustomExceptionHandler;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RecordSneakyThrowsExceptionHandler extends CustomExceptionHandler {
    private static final String SNEAKY_THROWS = "dev.sweety.record.annotations.SneakyThrows";

    @Override
    public boolean isHandled(@Nullable PsiElement element, @NotNull PsiClassType exceptionType, PsiElement topElement) {
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        return method != null && method.hasAnnotation(SNEAKY_THROWS);
    }
}
