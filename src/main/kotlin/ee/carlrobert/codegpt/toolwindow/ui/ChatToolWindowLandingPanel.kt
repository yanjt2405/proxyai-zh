package ee.carlrobert.codegpt.toolwindow.ui

import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import ee.carlrobert.codegpt.Icons
import ee.carlrobert.codegpt.CodeGPTBundle
import ee.carlrobert.codegpt.credentials.CredentialsStore
import ee.carlrobert.codegpt.credentials.CredentialsStore.CredentialKey.CodeGptApiKey
import ee.carlrobert.codegpt.settings.GeneralSettings
import ee.carlrobert.codegpt.settings.prompts.ChatActionsState
import ee.carlrobert.codegpt.settings.models.ModelSettings
import ee.carlrobert.codegpt.settings.service.FeatureType
import ee.carlrobert.codegpt.settings.service.ServiceType
import ee.carlrobert.codegpt.settings.service.codegpt.CodeGPTServiceConfigurable
import ee.carlrobert.codegpt.ui.UIUtil
import ee.carlrobert.codegpt.ui.UIUtil.createTextPane
import ee.carlrobert.codegpt.util.ApplicationUtil
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.ActionListener
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel

class ChatToolWindowLandingPanel(onAction: (LandingPanelAction, Point) -> Unit) : ResponseMessagePanel() {

    init {
        addContent(createContent(onAction))
    }

    private fun createContent(onAction: (LandingPanelAction, Point) -> Unit): JPanel {
        return BorderLayoutPanel().apply {
            add(
                BorderLayoutPanel().apply {
                    isOpaque = false
                    apiKeyPanel()?.let { addToTop(it) }
                    addToCenter(createTextPane(getWelcomeMessage(), false))
                },
                BorderLayout.NORTH
            )
            add(createActionsListPanel(onAction), BorderLayout.CENTER)
            add(createTextPane(getCautionMessage(), false), BorderLayout.SOUTH)
        }
    }

    private fun apiKeyPanel(): JPanel? {
        val provider = ModelSettings.getInstance().getServiceForFeature(FeatureType.CHAT)
        if (provider != ServiceType.PROXYAI || CredentialsStore.isCredentialSet(CodeGptApiKey)) {
            return null
        }

        return BorderLayoutPanel().apply {
            isOpaque = false
            addToCenter(
                createTextPane(
                    """
                    <html>
                    <p style="margin-top: 4px; margin-bottom: 4px;">
                      ${CodeGPTBundle.get("landing.missingKey")}
                    </p>
                    <p style="margin-top: 4px; margin-bottom: 4px;">
                      ${CodeGPTBundle.get("landing.signUp")}
                    </p>
                    </html>
                    """.trimIndent(),
                    false
                ) { event ->
                    if (event.eventType == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED &&
                        event.description == "#OPEN_SETTINGS"
                    ) {
                        ShowSettingsUtil.getInstance().showSettingsDialog(
                            ApplicationUtil.findCurrentProject(),
                            CodeGPTServiceConfigurable::class.java
                        )
                    } else {
                        UIUtil.handleHyperlinkClicked(event)
                    }
                }
            )
            border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)
        }
    }

    private fun createActionsListPanel(onAction: (LandingPanelAction, Point) -> Unit): JPanel {
        val listPanel = JPanel()
        listPanel.layout = BoxLayout(listPanel, BoxLayout.PAGE_AXIS)
        listPanel.border = JBUI.Borders.emptyLeft(4)
        listPanel.add(Box.createVerticalStrut(4))
        listPanel.add(createEditorActionLink(LandingPanelAction.EXPLAIN, onAction))
        listPanel.add(Box.createVerticalStrut(4))
        listPanel.add(createEditorActionLink(LandingPanelAction.WRITE_TESTS, onAction))
        listPanel.add(Box.createVerticalStrut(4))
        listPanel.add(createEditorActionLink(LandingPanelAction.FIND_BUGS, onAction))
        listPanel.add(Box.createVerticalStrut(4))
        return listPanel
    }

    private fun createEditorActionLink(
        action: LandingPanelAction,
        onAction: (LandingPanelAction, Point) -> Unit
    ): ActionLink {
        return ActionLink(action.userMessage, ActionListener { event ->
            onAction(action, (event.source as ActionLink).locationOnScreen)
        }).apply {
            icon = Icons.Sparkle
        }
    }

    private fun getWelcomeMessage(): String {
        return """
            <html>
            <p style="margin-top: 4px; margin-bottom: 4px;">${CodeGPTBundle.get("landing.welcome", GeneralSettings.getCurrentState().displayName)}</p>
            </html>
        """.trimIndent()
    }

    private fun getCautionMessage(): String {
        return """
            <html>
            <p style="margin-top: 4px; margin-bottom: 4px;">${CodeGPTBundle.get("landing.caution")}</p>
            </html>
        """.trimIndent()
    }
}

enum class LandingPanelAction(
    val label: String,
    val userMessage: String,
    val prompt: String
) {
    FIND_BUGS(
        "Find Bugs",
        CodeGPTBundle.get("landing.findBugs"),
        ChatActionsState.DEFAULT_FIND_BUGS_PROMPT
    ),
    WRITE_TESTS(
        "Write Tests",
        CodeGPTBundle.get("landing.writeTests"),
        ChatActionsState.DEFAULT_WRITE_TESTS_PROMPT
    ),
    EXPLAIN(
        "Explain",
        CodeGPTBundle.get("landing.explain"),
        ChatActionsState.DEFAULT_EXPLAIN_PROMPT
    )
}
