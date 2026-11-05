package ee.carlrobert.codegpt.actions.editor;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.awt.RelativePoint;
import ee.carlrobert.codegpt.CodeGPTBundle;
import ee.carlrobert.codegpt.completions.CompletionRequestUtil;
import ee.carlrobert.codegpt.conversations.message.Message;
import ee.carlrobert.codegpt.settings.prompts.ChatActionsState;
import ee.carlrobert.codegpt.settings.prompts.PromptsSettings;
import ee.carlrobert.codegpt.toolwindow.chat.ChatToolWindowContentManager;
import java.awt.event.MouseEvent;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Actions shared by the Java and Kotlin method gutter markers. */
public final class MethodChatActions {

  private MethodChatActions() {}

  public static void showPopup(@NotNull PsiElement method, @NotNull MouseEvent event) {
    SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(method);
    DefaultActionGroup actions = new DefaultActionGroup();
    actions.add(createAction(pointer, "EXPLAIN", "methodActions.explain",
        ChatActionsState.Companion.getDEFAULT_EXPLAIN_PROMPT()));
    actions.add(createAction(pointer, "WRITE_TESTS", "methodActions.writeTests",
        ChatActionsState.Companion.getDEFAULT_WRITE_TESTS_PROMPT()));
    actions.add(createAction(pointer, "FIND_BUGS", "methodActions.findBugs",
        ChatActionsState.Companion.getDEFAULT_FIND_BUGS_PROMPT()));

    DataContext context = DataManager.getInstance().getDataContext(event.getComponent());
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
        CodeGPTBundle.get("methodActions.title"), actions, context,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
    popup.show(new RelativePoint(event));
  }

  private static AnAction createAction(
      SmartPsiElementPointer<PsiElement> pointer,
      String code,
      String labelKey,
      String fallbackPrompt) {
    return new AnAction(CodeGPTBundle.get(labelKey)) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        PsiElement method = pointer.getElement();
        if (method == null || !method.isValid()) {
          return;
        }

        String path = method.getContainingFile().getVirtualFile() != null
            ? method.getContainingFile().getVirtualFile().getPath()
            : method.getContainingFile().getName();
        String formattedCode = CompletionRequestUtil.formatCode(method.getText(), path);
        var prompts = ApplicationManager.getApplication()
            .getService(PromptsSettings.class).getState().getChatActions().getPrompts();
        String prompt = prompts.stream()
            .filter(item -> Objects.equals(item.getCode(), code))
            .map(item -> item.getInstructions())
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(fallbackPrompt);
        String request = prompt.contains("{SELECTION}")
            ? prompt.replace("{SELECTION}", formattedCode)
            : prompt + "\n\n" + formattedCode;
        String message = request
            + "\n\n" + CodeGPTBundle.get("methodActions.replyChinese");
        var chat = method.getProject().getService(ChatToolWindowContentManager.class);
        chat.displayChatTab();
        chat.sendMessage(new Message(message));
      }
    };
  }
}
