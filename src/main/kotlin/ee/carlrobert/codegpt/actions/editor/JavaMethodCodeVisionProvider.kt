package ee.carlrobert.codegpt.actions.editor

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import ee.carlrobert.codegpt.CodeGPTBundle

class JavaMethodCodeVisionProvider : DaemonBoundCodeVisionProvider {
    override val id = "proxyai.java.method.actions"
    override val groupId = "proxyai.method.actions"
    override val name: String get() = CodeGPTBundle.get("methodActions.title")
    override val defaultAnchor = CodeVisionAnchorKind.Top
    override val relativeOrderings: List<CodeVisionRelativeOrdering> = emptyList()

    override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> =
        PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java).map { method ->
            val firstAnnotation = method.modifierList.annotations.firstOrNull()
            methodCodeVisionEntry(method, firstAnnotation?.textRange?.startOffset ?: method.textRange.startOffset, id)
        }
}
