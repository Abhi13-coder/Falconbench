package com.falconbench.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Tiny JSON store — no Room dependency, survives restarts.
 * Models list, sessions, memory notes.
 */
class LocalStore(context: Context) {
    private val dir = File(context.filesDir, "falconbench").also { it.mkdirs() }
    private val modelsFile = File(dir, "models.json")
    private val sessionsFile = File(dir, "sessions.json")
    private val memoryFile = File(dir, "memory.json")
    private val prefs = context.getSharedPreferences("falconbench", Context.MODE_PRIVATE)

    var activeModelId: String?
        get() = prefs.getString("active_model_id", null)
        set(v) = prefs.edit().putString("active_model_id", v).apply()

    var activeSessionId: String?
        get() = prefs.getString("active_session_id", null)
        set(v) = prefs.edit().putString("active_session_id", v).apply()

    fun loadModels(): List<ModelEntry> {
        if (!modelsFile.exists()) return emptyList()
        val arr = JSONArray(modelsFile.readText())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ModelEntry(
                id = o.getString("id"),
                displayName = o.getString("displayName"),
                filePath = o.getString("filePath"),
                sizeBytes = o.getLong("sizeBytes"),
                sourceUri = o.optString("sourceUri").ifBlank { null },
                addedAt = o.optLong("addedAt", 0L)
            )
        }
    }

    fun saveModels(list: List<ModelEntry>) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id)
                put("displayName", m.displayName)
                put("filePath", m.filePath)
                put("sizeBytes", m.sizeBytes)
                put("sourceUri", m.sourceUri)
                put("addedAt", m.addedAt)
            })
        }
        modelsFile.writeText(arr.toString())
    }

    fun loadSessions(): List<Session> {
        if (!sessionsFile.exists()) return emptyList()
        val arr = JSONArray(sessionsFile.readText())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val msgs = o.optJSONArray("messages") ?: JSONArray()
            val messageList = (0 until msgs.length()).map { j ->
                val mj = msgs.getJSONObject(j)
                ChatMessage(
                    role = ChatMessage.Role.valueOf(mj.getString("role")),
                    text = mj.getString("text"),
                    metrics = mj.optString("metrics").ifBlank { null }
                )
            }
            Session(
                id = o.getString("id"),
                title = o.getString("title"),
                modelId = o.optString("modelId").ifBlank { null },
                createdAt = o.optLong("createdAt", 0L),
                updatedAt = o.optLong("updatedAt", 0L),
                messages = messageList
            )
        }
    }

    fun saveSessions(list: List<Session>) {
        val arr = JSONArray()
        list.forEach { s ->
            val msgs = JSONArray()
            s.messages.forEach { m ->
                msgs.put(JSONObject().apply {
                    put("role", m.role.name)
                    put("text", m.text)
                    put("metrics", m.metrics)
                })
            }
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("title", s.title)
                put("modelId", s.modelId)
                put("createdAt", s.createdAt)
                put("updatedAt", s.updatedAt)
                put("messages", msgs)
            })
        }
        sessionsFile.writeText(arr.toString())
    }

    fun loadMemory(): List<MemoryNote> {
        if (!memoryFile.exists()) return emptyList()
        val arr = JSONArray(memoryFile.readText())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            MemoryNote(
                id = o.getString("id"),
                text = o.getString("text"),
                createdAt = o.optLong("createdAt", 0L)
            )
        }
    }

    fun saveMemory(list: List<MemoryNote>) {
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(JSONObject().apply {
                put("id", n.id)
                put("text", n.text)
                put("createdAt", n.createdAt)
            })
        }
        memoryFile.writeText(arr.toString())
    }
}
