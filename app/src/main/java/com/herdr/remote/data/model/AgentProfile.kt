package com.herdr.remote.data.model

data class AgentProfile(
    val id: String,
    val name: String,
    val role: String,
    val avatarEmoji: String,
    val defaultModel: String,
    val description: String,
    val systemPrompt: String,
    val capabilities: List<String>
) {
    companion object {
        val PRESET_PROFILES = listOf(
            AgentProfile(
                id = "herdr-commander",
                name = "Herdr Orchestrator",
                role = "General Autonomous Agent",
                avatarEmoji = "⚡",
                defaultModel = "openrouter/auto",
                description = "Coordinates complex multi-step workflows, tool execution, and remote command orchestration.",
                systemPrompt = "You are Herdr Orchestrator, an elite autonomous AI system. Provide structured, high-signal responses with actionable steps and direct results.",
                capabilities = listOf("Terminal Commands", "File Operations", "Multi-Agent Coordination", "Web Search")
            ),
            AgentProfile(
                id = "code-artisan",
                name = "Code Artisan",
                role = "Senior Software Architect",
                avatarEmoji = "💻",
                defaultModel = "openrouter/auto",
                description = "Specialized in architecture, writing clean idiomatic code, debugging, and refactoring.",
                systemPrompt = "You are Code Artisan. Write clean, production-grade code with thorough explanation, best practices, and elegant patterns.",
                capabilities = listOf("Code Generation", "Refactoring", "Linting & Testing", "Architecture Review")
            ),
            AgentProfile(
                id = "research-specialist",
                name = "Deep Researcher",
                role = "Information Synthesis Agent",
                avatarEmoji = "🔍",
                defaultModel = "openrouter/auto",
                description = "Synthesizes documents, parses complex PDFs and data feeds, and creates exhaustive executive summaries.",
                systemPrompt = "You are Deep Researcher. Synthesize information with rigorous clarity, cite evidence, and deliver structured briefings.",
                capabilities = listOf("PDF Analysis", "Deep Fact Checking", "Data Extraction", "Comparative Synthesis")
            ),
            AgentProfile(
                id = "devops-engineer",
                name = "Autonomous Ops",
                role = "Infrastructure & Cloud Agent",
                avatarEmoji = "🚀",
                defaultModel = "openrouter/auto",
                description = "Monitors systems, runs remote diagnostics, deploys builds, and manages Kubernetes/Docker clusters.",
                systemPrompt = "You are Autonomous Ops. Provide safe, verified command proposals and infrastructure insights.",
                capabilities = listOf("Docker & K8s", "CI/CD Pipeline", "Log Analysis", "Server Diagnostics")
            )
        )
    }
}
