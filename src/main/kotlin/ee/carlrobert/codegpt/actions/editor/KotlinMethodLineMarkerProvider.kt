package ee.carlrobert.codegpt.actions.editor

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import ee.carlrobert.codegpt.CodeGPTBundle
import ee.carlrobert.codegpt.Icons
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinMethodLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val function = element.parent as? KtNamedFunction ?: return null
        if (function.nameIdentifier != element) return null

        return LineMarkerInfo(
            element,
            element.textRange,
            Icons.DefaultSmall,
            { CodeGPTBundle.get("methodActions.tooltip") },
            { event, _ -> MethodChatActions.showPopup(function, event) },
            GutterIconRenderer.Alignment.CENTER,
            { CodeGPTBundle.get("methodActions.tooltip") }
        )
    }
}
