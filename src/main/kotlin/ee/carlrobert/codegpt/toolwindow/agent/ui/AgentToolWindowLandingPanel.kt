package ee.carlrobert.codegpt.toolwindow.agent.ui

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import ee.carlrobert.codegpt.agent.ProxyAIAgent.loadProjectInstructions
import ee.carlrobert.codegpt.credentials.CredentialsStore
import ee.carlrobert.codegpt.credentials.CredentialsStore.CredentialKey.CodeGptApiKey
import ee.carlrobert.codegpt.agent.history.AgentCheckpointConversationMapper
import ee.carlrobert.codegpt.agent.history.AgentCheckpointHistoryService
import ee.carlrobert.codegpt.agent.history.AgentHistoryThreadSummary
import ee.carlrobert.codegpt.settings.GeneralSettings
import ee.carlrobert.codegpt.settings.ProxyAISettingsService
import ee.carlrobert.codegpt.settings.ProxyAISubagent
import ee.carlrobert.codegpt.settings.agents.SubagentsConfigurable
import ee.carlrobert.codegpt.settings.hooks.HookConfig
import ee.carlrobert.codegpt.settings.hooks.HookConfiguration
import ee.carlrobert.codegpt.settings.models.ModelSettings
import ee.carlrobert.codegpt.settings.service.FeatureType
import ee.carlrobert.codegpt.settings.service.ServiceType
import ee.carlrobert.codegpt.settings.service.codegpt.CodeGPTServiceConfigurable
import ee.carlrobert.codegpt.settings.skills.SkillDescriptor
import ee.carlrobert.codegpt.settings.skills.SkillDiscoveryService
import ee.carlrobert.codegpt.tokens.TokenComputationService
import ee.carlrobert.codegpt.toolwindow.agent.AgentToolWindowContentManager
import ee.carlrobert.codegpt.toolwindow.agent.history.AgentHistoryListPanel
import ee.carlrobert.codegpt.toolwindow.ui.ResponseMessagePanel
import ee.carlrobert.codegpt.ui.UIUtil
import ee.carlrobert.codegpt.ui.UIUtil.createTextPane
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import ee.carlrobert.codegpt.util.coroutines.DisposableCoroutineScope
import com.intellij.openapi.Disposable
import ee.carlrobert.codegpt.CodeGPTBundle
import java.awt.Color
import java.awt.Desktop
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel

class AgentToolWindowLandingPanel(private val project: Project) : BorderLayoutPanel(), Disposable {

    companion object {
        private val logger = thisLogger()
    }

    private val historyService = project.service<AgentCheckpointHistoryService>()
    private val historyListPanel = AgentHistoryListPanel(defaultLimit = 5)
    private val backgroundScope = DisposableCoroutineScope(Dispatchers.IO)
    private var refreshHistory = true
    @Volatile
    private var disposed = false

    private fun createLabel(text: String) = JBLabel(text)
    private fun createLink(text: String, onClick: () -> Unit) = ActionLink(text) { onClick() }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
    }

    init {
        isOpaque = false
        historyListPanel.onOpen = { thread -> openCheckpointThread(thread) }
        historyListPanel.onLoadPage = { query, offset, limit, onResult ->
            loadHistoryPage(query, offset, limit, onResult)
        }
        addToCenter(buildContent())
        loadHistory()
    }

    private fun buildContent(): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(primaryMessagePanel())
            apiKeyPanel()?.let { add(it) }
        }
    }

    private fun primaryMessagePanel(): ResponseMessagePanel {
        return ResponseMessagePanel().apply {
            addContent(
                BorderLayoutPanel().apply {
                    border = JBUI.Borders.empty(0)
                    addToTop(topPanel())
                    addToCenter(centerPanel())
                }
            )
        }
    }

    private fun topPanel(): JPanel {
        return BorderLayoutPanel().apply {
            isOpaque = false
            addToCenter(createTextPane(welcomeMessage(), false))
        }
    }

    private fun apiKeyPanel(): ResponseMessagePanel? {
        val provider = ModelSettings.getInstance().getServiceForFeature(FeatureType.AGENT)
        if (provider != ServiceType.PROXYAI || CredentialsStore.isCredentialSet(CodeGptApiKey)) {
            return null
        }

        return ResponseMessagePanel().apply {
            border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)
            addContent(
                createTextPane(
                    "<html><p>${CodeGPTBundle.get("landing.missingKey")}</p>" +
                        "<p>${CodeGPTBundle.get("landing.signUp")}</p></html>",
                    false
                ) { event ->
                    if (event.eventType == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED &&
                        event.description == "#OPEN_SETTINGS"
                    ) {
                        ShowSettingsUtil.getInstance().showSettingsDialog(
                            project,
                            CodeGPTServiceConfigurable::class.java
                        )
                    } else {
                        UIUtil.handleHyperlinkClicked(event)
                    }
                }
            )
        }
    }

    private fun centerPanel(): JPanel {
        val panel = BorderLayoutPanel()
        panel.addToTop(topSectionsPanel())
        panel.addToCenter(previousChatsPanel())
        return panel
    }

    private fun topSectionsPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.alignmentX = LEFT_ALIGNMENT
        panel.add(actionsListPanel())
        panel.add(Box.createVerticalStrut(12))
        panel.add(projectInfoPanel())
        panel.add(Box.createVerticalStrut(12))
        return panel
    }

    private fun actionsListPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.emptyTop(8)
        panel.alignmentX = LEFT_ALIGNMENT

        panel.add(createLabel(CodeGPTBundle.get("agent.landing.quickActions")))
        panel.add(Box.createVerticalStrut(6))

        val itemsPanel = JPanel()
        itemsPanel.layout = BoxLayout(itemsPanel, BoxLayout.Y_AXIS)
        itemsPanel.border = JBUI.Borders.emptyLeft(16)
        itemsPanel.alignmentX = LEFT_ALIGNMENT

        itemsPanel.add(createLink(CodeGPTBundle.get("agent.landing.manageSubagents")) {
            ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, SubagentsConfigurable::class.java)
        })
        itemsPanel.add(Box.createVerticalStrut(6))
        itemsPanel.add(createLink(CodeGPTBundle.get("agent.landing.documentation")) {
            try {
                Desktop.getDesktop().browse(URI("https://docs.tryproxy.io/agent"))
            } catch (e: Exception) {
                logger.error(e)
            }
        })

        panel.add(itemsPanel)
        return panel
    }

    private fun projectInfoPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty()
        panel.alignmentX = LEFT_ALIGNMENT

        panel.add(createLabel(CodeGPTBundle.get("agent.landing.projectSetup")))
        panel.add(Box.createVerticalStrut(6))

        val container = JPanel()
        container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
        container.border = JBUI.Borders.emptyLeft(16)
        container.alignmentX = LEFT_ALIGNMENT

        val vf = findProxyAiFile()
        if (vf == null) {
            container.add(createLabel(CodeGPTBundle.get("agent.landing.projectFileMissing")))
            container.add(Box.createVerticalStrut(6))
            container.add(createLink(CodeGPTBundle.get("agent.landing.initializeProject")) { createProxyAiFile() })
        } else {
            val topRow = JPanel()
            topRow.layout = BoxLayout(topRow, BoxLayout.X_AXIS)
            topRow.alignmentX = LEFT_ALIGNMENT
            topRow.add(createLink("PROXYAI.md") { openProxyAiFile() })

            topRow.add(JBLabel(" • "))

            val fileSize = vf.length
            val fileSizeLabel = JBLabel(formatFileSize(fileSize))
            fileSizeLabel.foreground = SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor
            topRow.add(fileSizeLabel)

            topRow.add(JBLabel(" • "))

            val tokens = countFileTokens(vf)
            topRow.add(JBLabel(CodeGPTBundle.get("agent.landing.tokens") + " "))
            val tokensLabel = JBLabel(formatTokens(tokens))
            tokensLabel.foreground = healthColor(tokens)
            topRow.add(tokensLabel)

            val settingsService = project.service<ProxyAISettingsService>()
            val skills = project.service<SkillDiscoveryService>().listSkills()
            val subagents = settingsService.getSubagents()
            val hookEntries = collectHookEntries(settingsService.getHooks())
            val enabledHooksCount = hookEntries.count { it.hook.enabled }

            val detailsRow = JPanel()
            detailsRow.layout = BoxLayout(detailsRow, BoxLayout.X_AXIS)
            detailsRow.alignmentX = LEFT_ALIGNMENT
            detailsRow.add(
                createHoverDetailsLabel(
                    text = CodeGPTBundle.get("agent.landing.skillsCount", skills.size),
                    tooltip = buildSkillsTooltip(skills)
                )
            )
            detailsRow.add(createDetailsSeparator())
            detailsRow.add(
                createHoverDetailsLabel(
                    text = CodeGPTBundle.get("agent.landing.hooksCount", enabledHooksCount),
                    tooltip = buildHooksTooltip(hookEntries)
                )
            )
            detailsRow.add(createDetailsSeparator())
            detailsRow.add(
                createHoverDetailsLabel(
                    text = CodeGPTBundle.get("agent.landing.subagentsCount", subagents.size),
                    tooltip = buildSubagentsTooltip(subagents)
                )
            )

            container.add(topRow)
            container.add(Box.createVerticalStrut(2))
            container.add(detailsRow)
        }

        panel.add(container)
        return panel
    }

    private fun previousChatsPanel(): JPanel {
        val panel = BorderLayoutPanel().apply {
            border = JBUI.Borders.empty()
        }
        panel.addToTop(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = LEFT_ALIGNMENT
            isOpaque = false
            add(createLabel(CodeGPTBundle.get("agent.landing.previousChats")))
            add(Box.createVerticalStrut(6))
        })
        panel.addToCenter(historyListPanel)
        return panel
    }

    private fun findProxyAiFile() =
        project.basePath?.let {
            LocalFileSystem.getInstance().findFileByPath(Path.of(it, "PROXYAI.md").toString())
        }

    private fun createProxyAiFile() {
        val basePath = project.basePath ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            val dir = LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath)
                ?: return@runWriteCommandAction
            val existing = dir.findChild("PROXYAI.md")
            val vf = existing ?: dir.createChildData(this, "PROXYAI.md")
            vf.setBinaryContent(defaultProxyAiContent().toByteArray(StandardCharsets.UTF_8))
        }
        refresh()
    }

    private fun openProxyAiFile() {
        val vf = findProxyAiFile() ?: return
        FileEditorManager.getInstance(project).openFile(vf, true)
    }

    private fun refresh() {
        removeAll()
        addToCenter(buildContent())
        loadHistory()
        revalidate()
        repaint()
    }

    private fun loadHistory() {
        refreshHistory = true
        historyListPanel.reload()
    }

    private fun loadHistoryPage(
        query: String,
        offset: Int,
        limit: Int,
        onResult: (List<AgentHistoryThreadSummary>, Boolean, Int) -> Unit
    ) {
        if (disposed || project.isDisposed) return
        backgroundScope.launch {
            val page = try {
                historyService.listThreadsPage(
                    query = query,
                    offset = offset,
                    limit = limit,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                logger.warn("Failed to load checkpoint history", throwable)
                null
            }

            if (disposed || project.isDisposed) return@launch
            runInEdt {
                if (disposed || project.isDisposed) return@runInEdt
                if (page != null) {
                    refreshHistory = false
                }
                onResult(page?.items.orEmpty(), page?.hasMore == true, page?.total ?: 0)
            }
        }
    }

    private fun openCheckpointThread(thread: AgentHistoryThreadSummary) {
        if (disposed || project.isDisposed) return
        backgroundScope.launch {
            val checkpoint = try {
                historyService.loadCheckpoint(thread.latest)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                logger.warn("Failed to open checkpoint thread ${thread.agentId}", throwable)
                null
            } ?: return@launch

            val conversation = try {
                AgentCheckpointConversationMapper.toConversation(
                    checkpoint = checkpoint,
                    projectInstructions = loadProjectInstructions(project.basePath)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                logger.warn("Failed to open checkpoint thread ${thread.agentId}", throwable)
                null
            } ?: return@launch

            if (disposed || project.isDisposed) return@launch
            runInEdt {
                if (disposed || project.isDisposed) return@runInEdt
                project.service<AgentToolWindowContentManager>()
                    .openCheckpointConversation(thread, conversation, thread.latest)
            }
        }
    }

    override fun dispose() {
        disposed = true
        backgroundScope.dispose()
    }

    private fun welcomeMessage(): String {
        val name = GeneralSettings.getCurrentState().displayName
        return "<html><p>${CodeGPTBundle.get("agent.landing.welcome", name)}</p></html>"
    }

    private fun defaultProxyAiContent() = CodeGPTBundle.get("agent.project.defaultInstructions")

    private fun countFileTokens(vf: VirtualFile): Int {
        val text = runCatching { VfsUtilCore.loadText(vf) }.getOrNull() ?: ""
        return TokenComputationService.getInstance().countTextTokens(text)
    }

    private fun formatTokens(n: Int): String =
        if (n < 1000) n.toString() else String.format("%.1fk", n / 1000.0)

    private fun healthColor(tokens: Int): Color {
        val green = Color(76, 175, 80)
        val yellow = Color(255, 193, 7)
        val red = Color(244, 67, 54)
        if (tokens <= 4096) return green
        if (tokens <= 8192) return lerpColor(green, yellow, (tokens - 4096f) / (8192f - 4096f))
        if (tokens <= 16384) return lerpColor(yellow, red, (tokens - 8192f) / (16384f - 8192f))
        return red
    }

    private fun lerpColor(a: Color, b: Color, tRaw: Float): Color {
        val t = tRaw.coerceIn(0f, 1f)
        val r = (a.red + (b.red - a.red) * t).toInt()
        val g = (a.green + (b.green - a.green) * t).toInt()
        val bl = (a.blue + (b.blue - a.blue) * t).toInt()
        return Color(r, g, bl)
    }

    private fun createHoverDetailsLabel(text: String, tooltip: String): JBLabel {
        return JBLabel(text).apply {
            foreground = SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor
            toolTipText = tooltip
        }
    }

    private fun createDetailsSeparator(): JBLabel {
        return JBLabel(" • ").apply {
            foreground = SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor
        }
    }

    private fun collectHookEntries(configuration: HookConfiguration): List<HookEntry> = buildList {
        addAll(configuration.beforeToolUse.map { HookEntry(CodeGPTBundle.get("agent.hook.beforeTool"), it) })
        addAll(configuration.afterToolUse.map { HookEntry(CodeGPTBundle.get("agent.hook.afterTool"), it) })
        addAll(configuration.subagentStart.map { HookEntry(CodeGPTBundle.get("agent.hook.subagentStart"), it) })
        addAll(configuration.subagentStop.map { HookEntry(CodeGPTBundle.get("agent.hook.subagentStop"), it) })
        addAll(configuration.beforeShellExecution.map { HookEntry(CodeGPTBundle.get("agent.hook.beforeShell"), it) })
        addAll(configuration.afterShellExecution.map { HookEntry(CodeGPTBundle.get("agent.hook.afterShell"), it) })
        addAll(configuration.beforeReadFile.map { HookEntry(CodeGPTBundle.get("agent.hook.beforeRead"), it) })
        addAll(configuration.afterFileEdit.map { HookEntry(CodeGPTBundle.get("agent.hook.afterEdit"), it) })
        addAll(configuration.stop.map { HookEntry(CodeGPTBundle.get("agent.hook.stop"), it) })
    }

    private fun buildSkillsTooltip(skills: List<SkillDescriptor>): String {
        if (skills.isEmpty()) {
            return tooltipHtml(
                CodeGPTBundle.get("agent.landing.skills"),
                listOf(CodeGPTBundle.get("agent.landing.noSkills"))
            )
        }

        val lines = skills.take(5).map { "• ${it.name} — ${it.title}" }.toMutableList()
        val remaining = skills.size - lines.size
        if (remaining > 0) {
            lines.add(CodeGPTBundle.get("agent.landing.more", remaining))
        }

        return tooltipHtml(CodeGPTBundle.get("agent.landing.skillsCountParenthesized", skills.size), lines)
    }

    private fun buildHooksTooltip(hookEntries: List<HookEntry>): String {
        val enabled = hookEntries.filter { it.hook.enabled }
        if (hookEntries.isEmpty()) {
            return tooltipHtml(
                CodeGPTBundle.get("agent.landing.hooks"),
                listOf(CodeGPTBundle.get("agent.landing.noHooks"))
            )
        }

        val lines = mutableListOf<String>()
        enabled.take(4).forEach { entry ->
            lines.add("• ${entry.event}: ${entry.hook.command}")
        }
        val remainingEnabled = enabled.size - 4
        if (remainingEnabled > 0) {
            lines.add(CodeGPTBundle.get("agent.landing.moreEnabled", remainingEnabled))
        }

        return tooltipHtml(CodeGPTBundle.get("agent.landing.hooks"), lines)
    }

    private fun buildSubagentsTooltip(subagents: List<ProxyAISubagent>): String {
        if (subagents.isEmpty()) {
            return tooltipHtml(
                CodeGPTBundle.get("agent.landing.subagents"),
                listOf(CodeGPTBundle.get("agent.landing.noSubagents"))
            )
        }

        val lines = subagents.take(5).map { "• ${it.title}" }.toMutableList()
        val remaining = subagents.size - lines.size
        if (remaining > 0) {
            lines.add(CodeGPTBundle.get("agent.landing.more", remaining))
        }

        return tooltipHtml(CodeGPTBundle.get("agent.landing.subagentsCountParenthesized", subagents.size), lines)
    }

    private fun tooltipHtml(title: String, lines: List<String>): String {
        val escapedTitle = escapeHtml(title)
        val escapedLines = lines.joinToString("<br>") { escapeHtml(it) }
        return "<html><b>$escapedTitle</b><br>$escapedLines</html>"
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private data class HookEntry(
        val event: String,
        val hook: HookConfig
    )
}
