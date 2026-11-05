package ee.carlrobert.codegpt.actions.editor;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import ee.carlrobert.codegpt.CodeGPTBundle;
import ee.carlrobert.codegpt.Icons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A marker on the method name, so it stays visible when the method body is off screen. */
public final class JavaMethodLineMarkerProvider implements LineMarkerProvider {

  @Override
  public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
    if (!(element instanceof PsiIdentifier identifier)
        || !(identifier.getParent() instanceof PsiMethod method)
        || method.getNameIdentifier() != identifier) {
      return null;
    }

    return new LineMarkerInfo<>(identifier, identifier.getTextRange(), Icons.DefaultSmall,
        ignored -> CodeGPTBundle.get("methodActions.tooltip"),
        (event, ignored) -> MethodChatActions.showPopup(method, event),
        GutterIconRenderer.Alignment.CENTER,
        () -> CodeGPTBundle.get("methodActions.tooltip"));
  }
}
