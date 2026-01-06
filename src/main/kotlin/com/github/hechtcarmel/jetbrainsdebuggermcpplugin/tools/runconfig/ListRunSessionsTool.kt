package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.runconfig

import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.server.models.ToolAnnotations
import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.models.RunSessionInfo
import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.models.RunSessionListResult
import com.intellij.openapi.project.Project
import com.intellij.execution.process.ProcessHandler
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * MCP tool for listing all active run sessions in the IDE.
 * 
 * This tool retrieves information about running processes including their IDs,
 * names, and current states. It is useful for discovering available sessions
 * when multiple run sessions are active.
 * 
 * @param project The IntelliJ project context
 */
class ListRunSessionsTool : AbstractMcpTool() {

    /**
     * Lists all active run sessions with their IDs, names, and states.
     * Use to discover session IDs when multiple run sessions are running.
     */
    override val name = "list_run_sessions"

    override val description = """
        Lists all active run sessions with their IDs, names, and states.
        Use to discover session IDs when multiple run sessions are running.
    """.trimIndent()

    override val annotations = ToolAnnotations.readOnly("List Run Sessions")

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            val (propName, propSchema) = projectPathProperty()
            put(propName, propSchema)
        }
        put("required", buildJsonArray { })
        put("additionalProperties", false)
    }

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        val executionManager = com.intellij.execution.ExecutionManager.getInstance(project)
        val runningProcesses: Array<ProcessHandler> = executionManager.getRunningProcesses()

        val sessionInfos: List<RunSessionInfo> = runningProcesses.map { processHandler ->
            val processId = getProcessId(processHandler)
            RunSessionInfo(
                id = processId?.toString() ?: processHandler.hashCode().toString(),
                name = processHandler.toString(),
                state = if (processHandler.isProcessTerminated) "stopped" else "running",
                processId = processId,
                executorId = null,
                runConfigurationName = null
            )
        }

        return createJsonResult(RunSessionListResult(
            sessions = sessionInfos,
            totalCount = sessionInfos.size
        ))
    }

    /**
     * Retrieves the process ID from a ProcessHandler using reflection.
     * 
     * @param processHandler The process handler to extract the ID from
     * @return The process ID if available, null otherwise
     */
    private fun getProcessId(processHandler: ProcessHandler): Long? {
        return try {
            val process = processHandler.javaClass.getMethod("getProcess").invoke(processHandler)
            if (process is Process) {
                process.pid()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
