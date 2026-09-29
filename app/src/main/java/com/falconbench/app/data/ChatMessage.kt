package com.falconbench.app.data

data class ChatMessage(
    val role: Role,
    val text: String,
    val metrics: String? = null
) {
    enum class Role { USER, ASSISTANT, SYSTEM, METRICS }
}
