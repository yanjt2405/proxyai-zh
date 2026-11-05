package ee.carlrobert.codegpt.actions.editor

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import ee.carlrobert.codegpt.CodeGPTBundle

class MethodCodeVisionGroupSettingProvider : CodeVisionGroupSettingProvider {
    override val groupId = "proxyai.method.actions"
    override val groupName: String get() = CodeGPTBundle.get("methodActions.title")
    override val description: String get() = CodeGPTBundle.get("methodActions.tooltip")
}
