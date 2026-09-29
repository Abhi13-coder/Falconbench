package com.falconbench.app.data

import java.util.UUID

/** Registered GGUF on disk (imported). Only one can be ACTIVE in RAM at a time on 32-bit. */
data class ModelEntry(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val filePath: String,
    val sizeBytes: Long,
    val sourceUri: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

/** One conversation thread bound to a model id (may outlive unload). */
data class Session(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val modelId: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList()
)

/** On-device “memory” notes injected into prompts (user-editable facts). */
data class MemoryNote(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

enum class AppPane { LAB, MODELS, HISTORY, MEMORY, CONTROLS }
