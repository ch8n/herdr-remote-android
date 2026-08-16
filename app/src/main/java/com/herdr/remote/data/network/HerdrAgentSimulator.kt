package com.herdr.remote.data.network

import com.herdr.remote.data.model.AgentConnectionStatus
import com.herdr.remote.data.model.AgentProfile
import com.herdr.remote.data.model.Attachment
import com.herdr.remote.data.model.AttachmentType
import com.herdr.remote.data.model.Message
import com.herdr.remote.data.model.MessageSender
import com.herdr.remote.data.model.MessageStatus
import com.herdr.remote.data.model.ToolExecution
import com.herdr.remote.data.model.ToolStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class SimulatedStep {
    data class StatusUpdate(val status: AgentConnectionStatus, val detail: String) : SimulatedStep()
    data class ToolStart(val tool: ToolExecution) : SimulatedStep()
    data class ToolEnd(val tool: ToolExecution) : SimulatedStep()
    data class ThoughtUpdate(val thought: String) : SimulatedStep()
    data class StreamToken(val token: String) : SimulatedStep()
    data class Complete(val message: Message) : SimulatedStep()
}

class HerdrAgentSimulator {

    fun generateAgentResponse(
        sessionId: String,
        prompt: String,
        attachments: List<Attachment>,
        agentProfile: AgentProfile
    ): Flow<SimulatedStep> = flow {
        // Step 1: Agent enters thinking phase
        emit(SimulatedStep.StatusUpdate(AgentConnectionStatus.THINKING, "Analyzing prompt and intent..."))
        delay(600)

        val hasPdf = attachments.any { it.type == AttachmentType.PDF }
        val hasImage = attachments.any { it.type == AttachmentType.IMAGE }

        val thoughtText = buildString {
            append("1. Intent recognition: User requests \"${prompt.take(60)}...\"\n")
            if (hasPdf) append("2. Attachment detected: Ingesting PDF binary metadata and parsing text vectors.\n")
            if (hasImage) append("2. Attachment detected: Visual feature extraction enabled.\n")
            append("3. Agent profile: ${agentProfile.name} (${agentProfile.role}).\n")
            append("4. Planning tool sequence and synthesising structured output.")
        }
        emit(SimulatedStep.ThoughtUpdate(thoughtText))

        // Step 2: Tool execution simulation
        emit(SimulatedStep.StatusUpdate(AgentConnectionStatus.EXECUTING_TOOL, "Executing verification tools..."))
        delay(500)

        val toolExecutions = mutableListOf<ToolExecution>()

        val isSensitiveCommand = prompt.contains("permission", ignoreCase = true) ||
                prompt.contains("deploy", ignoreCase = true) ||
                prompt.contains("delete", ignoreCase = true) ||
                prompt.contains("bash", ignoreCase = true) ||
                prompt.contains("sudo", ignoreCase = true) ||
                prompt.contains("install", ignoreCase = true)

        if (isSensitiveCommand) {
            val sensitiveTool = ToolExecution(
                toolName = "execute_remote_command",
                argumentsJson = "{\"command\": \"${prompt.take(40)}\", \"elevated\": true, \"requires_confirmation\": true}",
                status = ToolStatus.REQUIRES_APPROVAL,
                requiresPermission = true,
                permissionPrompt = "Agent requires confirmation to execute: '${prompt.take(50)}'"
            )
            toolExecutions.add(sensitiveTool)
            emit(SimulatedStep.ToolStart(sensitiveTool))
            delay(1200)
        } else if (hasPdf) {
            val pdfTool = ToolExecution(
                toolName = "pdf_text_extractor",
                argumentsJson = "{\"document\": \"${attachments.first { it.type == AttachmentType.PDF }.name}\", \"mode\": \"semantic_chunks\"}",
                status = ToolStatus.RUNNING
            )
            emit(SimulatedStep.ToolStart(pdfTool))
            delay(800)
            val pdfToolDone = pdfTool.copy(
                resultJson = "{\"pages_parsed\": 14, \"status\": \"indexed\", \"tokens_extracted\": 4820}",
                status = ToolStatus.SUCCESS,
                durationMs = 812
            )
            toolExecutions.add(pdfToolDone)
            emit(SimulatedStep.ToolEnd(pdfToolDone))
        } else {
            val tool1 = ToolExecution(
                toolName = "herdr_cluster_query",
                argumentsJson = "{\"query\": \"${prompt.take(30)}\", \"depth\": 2, \"timeout\": 5000}",
                status = ToolStatus.RUNNING
            )
            emit(SimulatedStep.ToolStart(tool1))
            delay(700)
            val tool1Done = tool1.copy(
                resultJson = "{\"status\": \"synced\", \"nodes_active\": 4, \"latency_ms\": 18}",
                status = ToolStatus.SUCCESS,
                durationMs = 690
            )
            toolExecutions.add(tool1Done)
            emit(SimulatedStep.ToolEnd(tool1Done))
        }

        // Step 3: Stream generated response
        emit(SimulatedStep.StatusUpdate(AgentConnectionStatus.STREAMING, "Generating response..."))

        val fullResponse = generateTextContent(prompt, attachments, agentProfile)
        val words = fullResponse.split(" ")

        val streamedSb = StringBuilder()
        for (i in words.indices) {
            val chunk = if (i == 0) words[i] else " " + words[i]
            streamedSb.append(chunk)
            emit(SimulatedStep.StreamToken(chunk))
            delay(35)
        }

        // Step 4: Final message complete
        val finalMessage = Message(
            sessionId = sessionId,
            sender = MessageSender.AGENT,
            content = streamedSb.toString(),
            status = MessageStatus.SENT,
            thought = thoughtText,
            toolExecutions = toolExecutions
        )

        emit(SimulatedStep.StatusUpdate(AgentConnectionStatus.ONLINE, "Ready for next instruction"))
        emit(SimulatedStep.Complete(finalMessage))
    }

    private fun generateTextContent(
        prompt: String,
        attachments: List<Attachment>,
        agent: AgentProfile
    ): String {
        val hasPdf = attachments.any { it.type == AttachmentType.PDF }
        val hasImage = attachments.any { it.type == AttachmentType.IMAGE }

        return when {
            hasPdf -> {
                val pdfName = attachments.first { it.type == AttachmentType.PDF }.name
                """
### 📄 Document Analysis: `$pdfName`

I have extracted and synthesized the contents of the attached PDF:

1. **Executive Overview**:
   - The document outlines core architecture components and integration specifications.
   - Identified **3 critical optimization points** and automated failover rules.

2. **Key Findings**:
   - **Throughput**: Scalable up to 25,000 req/sec with sub-millisecond p99 latency.
   - **Security**: Zero-trust credential isolation with cryptographic validation.
   - **Action Item**: Verify remote node endpoints and configure TLS certificates.

```kotlin
// Example Remote Node Dispatcher
val client = HerdrClient.Builder()
    .setEndpoint("wss://herdr-agent.internal/v1")
    .setAuthToken(token)
    .build()
```

Let me know if you would like me to extract specific sections or run a deep diff on this document.
                """.trimIndent()
            }
            hasImage -> {
                """
### 🖼️ Visual Context Processed

I analyzed the image attachment with high-resolution feature mapping:

- **Layout & Structure**: Clean visual hierarchy detected.
- **Key Elements**: Interactive touch targets, distinct color contrast, and balanced spatial padding.
- **Recommendation**: Ensure high-density assets are cached locally to minimize network roundtrips.

Ready for follow-up directives or code generation based on this design.
                """.trimIndent()
            }
            prompt.contains("code", ignoreCase = true) || prompt.contains("function", ignoreCase = true) || prompt.contains("android", ignoreCase = true) -> {
                """
### 🚀 Implementation Strategy

Here is the structured solution for: **$prompt**

```kotlin
class AgentOrchestrator(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun executeTask(command: String): Flow<AgentEvent> = flow {
        emit(AgentEvent.Started(command))
        val result = runAgentPipeline(command)
        emit(AgentEvent.Finished(result))
    }.flowOn(dispatcher)
}
```

#### Key Advantages:
- **Reactive Stream**: Non-blocking flow emits status updates directly to the Compose UI.
- **Robust Error Handling**: Automatic reconnection with exponential backoff.
- **Memory Efficient**: Zero unnecessary allocations during token streaming.
                """.trimIndent()
            }
            else -> {
                """
### ⚡ ${agent.name} Briefing

I have processed your instruction:
> *"$prompt"*

#### Execution Summary:
- **Agent Role**: ${agent.role}
- **Status**: Completed successfully without deviations.
- **Next Actions**: Ready for your next command, file attachment, or code review.

Feel free to speak via microphone or drop additional documents anytime!
                """.trimIndent()
            }
        }
    }
}
