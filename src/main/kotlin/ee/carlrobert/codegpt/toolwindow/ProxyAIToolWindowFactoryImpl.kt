package ee.carlrobert.codegpt.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import ee.carlrobert.codegpt.CodeGPTBundle
import ee.carlrobert.codegpt.toolwindow.agent.AgentToolWindowPanel
import ee.carlrobert.codegpt.toolwindow.chat.ChatToolWindowPanel
import ee.carlrobert.codegpt.toolwindow.history.ChatHistoryToolWindow
import javax.swing.JComponent

class ProxyAIToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val agentToolWindowPanel = AgentToolWindowPanel(project).also {
            Disposer.register(toolWindow.disposable, it)
        }
        val chatToolWindowPanel = ChatToolWindowPanel(project, toolWindow.disposable)
        val chatHistoryToolWindow = ChatHistoryToolWindow(project)

        addContent(toolWindow, agentToolWindowPanel, CodeGPTBundle.get("toolWindow.tab.agent"))
        addContent(toolWindow, chatToolWindowPanel, CodeGPTBundle.get("toolWindow.tab.chat"))
        addContent(toolWindow, chatHistoryToolWindow.getContent(), CodeGPTBundle.get("toolWindow.tab.history"))

        toolWindow.addContentManagerListener(object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                if (CodeGPTBundle.get("toolWindow.tab.history") == event.content.tabName && event.content.isSelected) {
                    chatHistoryToolWindow.refresh()
                }

                if (event.content.tabName == CodeGPTBundle.get("toolWindow.tab.agent") && !event.content.isSelected) {
                    agentToolWindowPanel.getTabbedPane().onTabHidden()
                }
            }
        })
    }

    private fun addContent(toolWindow: ToolWindow, panel: JComponent, displayName: String) {
        val contentManager = toolWindow.contentManager
        contentManager.addContent(contentManager.factory.createContent(panel, displayName, false))
    }
}
