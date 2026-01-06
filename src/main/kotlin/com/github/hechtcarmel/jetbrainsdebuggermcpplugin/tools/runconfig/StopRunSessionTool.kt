package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.runconfig

import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.server.models.ToolAnnotations
import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.models.StopSessionResult
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * MCP tool for stopping active run sessions in the IDE.
 * 
 * This tool terminates running processes by destroying their process handlers.
 * It is a destructive operation that cannot be undone. If no session ID is
 * provided, the first active run session will be stopped.
 * 
 * @param project The IntelliJ project context
 */
class StopRunSessionTool : AbstractMcpTool() {

    /**
     * Terminates a run session, stopping the running process.
     * Use to end a run session. This is a destructive operation that cannot be undone.
     */
    override val name = "stop_run_session"

    override val description = """
        Terminates a run session, stopping the running process.
        Use to end a run session. This is a destructive operation that cannot be undone.
    """.trimIndent()

    override val annotations = ToolAnnotations.mutable("Stop Run Session", destructive = true)

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            val (propName, propSchema) = projectPathProperty()
            put(propName, propSchema)
            val (sessionName, sessionSchema) = sessionIdProperty()
            put(sessionName, sessionSchema)
        }
        put("required", buildJsonArray { })
        put("additionalProperties", false)
    }

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        val sessionId = arguments["session_id"]?.jsonPrimitive?.content

        val processHandler = resolveRunSession(project, sessionId)
            ?: return createErrorResult(
                if (sessionId != null) "Run session not found: $sessionId"
                else "No active run session"
            )

        val resolvedSessionId = processHandler.hashCode().toString()
        val sessionName = processHandler.toString()

        return try {
            if (processHandler.isProcessTerminated) {
                createJsonResult(StopSessionResult(
                    sessionId = resolvedSessionId,
                    status = "already_stopped",
                    message = "Run session '$sessionName' was already stopped"
                ))
            } else {
                processHandler.destroyProcess()
                createJsonResult(StopSessionResult(
                    sessionId = resolvedSessionId,
                    status = "stopped",
                    message = "Run session '$sessionName' stopped"
                ))
            }
        } catch (e: Exception) {
            createErrorResult("Failed to stop session: ${e.message}")
        }
    }

    /**
     * Resolves a run session by its ID or returns the first active session.
     * 
     * @param project The IntelliJ project context
     * @param sessionId Optional session ID to search for
     * @return The matching ProcessHandler if found, null otherwise
     */
    private fun resolveRunSession(project: Project, sessionId: String?): ProcessHandler? {
        val executionManager = ExecutionManager.getInstance(project)
        val runningProcesses: Array<ProcessHandler> = executionManager.getRunningProcesses()

        if (sessionId == null) {
            return runningProcesses.firstOrNull { !it.isProcessTerminated }
        }

        return runningProcesses.find { processHandler ->
            val processId = getProcessId(processHandler)
            processHandler.hashCode().toString() == sessionId || processId?.toString() == sessionId
        }
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
