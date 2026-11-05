package ee.carlrobert.codegpt.actions.editor

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import ee.carlrobert.codegpt.CodeGPTBundle
import ee.carlrobert.codegpt.Icons

internal fun methodCodeVisionEntry(
    method: PsiElement,
    anchorOffset: Int,
    providerId: String
): Pair<TextRange, CodeVisionEntry> {
    val pointer = SmartPointerManager.createPointer(method)
    val entry = ClickableTextCodeVisionEntry(
        text = CodeGPTBundle.get("methodActions.codeVision"),
        providerId = providerId,
        onClick = { event, _ ->
            if (event != null) {
                pointer.element?.takeIf { it.isValid }?.let { MethodChatActions.showPopup(it, event) }
            }
        },
        icon = Icons.DefaultSmall,
        tooltip = CodeGPTBundle.get("methodActions.tooltip")
    )
    return TextRange(anchorOffset, method.textRange.endOffset) to entry
}
