package ee.carlrobert.codegpt.actions.editor

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import ee.carlrobert.codegpt.CodeGPTBundle
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinMethodCodeVisionProvider : DaemonBoundCodeVisionProvider {
    override val id = "proxyai.kotlin.method.actions"
    override val groupId = "proxyai.method.actions"
    override val name: String get() = CodeGPTBundle.get("methodActions.title")
    override val defaultAnchor = CodeVisionAnchorKind.Top
    override val relativeOrderings: List<CodeVisionRelativeOrdering> = emptyList()

    override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> =
        PsiTreeUtil.findChildrenOfType(file, KtNamedFunction::class.java).map { function ->
            val firstAnnotation = function.annotationEntries.firstOrNull()
            methodCodeVisionEntry(function, firstAnnotation?.textRange?.startOffset ?: function.textRange.startOffset, id)
        }
}
