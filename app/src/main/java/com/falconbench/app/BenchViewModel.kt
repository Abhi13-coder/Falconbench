package com.falconbench.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.falconbench.app.data.AppPane
import com.falconbench.app.data.ChatMessage
import com.falconbench.app.data.LocalStore
import com.falconbench.app.data.MemoryNote
import com.falconbench.app.data.ModelEntry
import com.falconbench.app.data.Session
import com.falconbench.app.native.LlamaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class BenchViewModel(app: Application) : AndroidViewModel(app) {

    private val store = LocalStore(app)

    val models = mutableStateListOf<ModelEntry>()
    val sessions = mutableStateListOf<Session>()
    val memoryNotes = mutableStateListOf<MemoryNote>()

    var pane by mutableStateOf(AppPane.LAB)
    var activeModelId by mutableStateOf<String?>(null)
        private set
    var activeSessionId by mutableStateOf<String?>(null)
        private set
    var weightsInRam by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf("Import a GGUF in Models")
        private set
    var contextUsedApprox by mutableStateOf(0)
        private set

    var nThreads by mutableStateOf(
        Runtime.getRuntime().availableProcessors().coerceIn(1, 6)
    )
    var nCtx by mutableStateOf(2048)
    var useMmap by mutableStateOf(true)
    var useMlock by mutableStateOf(false)
    var nice by mutableStateOf(0)
    var affinityMask by mutableStateOf(0L)
    var temperature by mutableStateOf(0.7f)
    var maxTokens by mutableStateOf(128)
    var topK by mutableStateOf(40)

    val nproc: Int get() = try { LlamaBridge.nativeNproc() } catch (_: Throwable) { 1 }

    val activeModel: ModelEntry? get() = models.find { it.id == activeModelId }
    val activeSession: Session? get() = sessions.find { it.id == activeSessionId }
    val labMessages: List<ChatMessage> get() = activeSession?.messages ?: emptyList()

    init {
        models.addAll(store.loadModels())
        sessions.addAll(store.loadSessions())
        memoryNotes.addAll(store.loadMemory())
        activeModelId = store.activeModelId
        activeSessionId = store.activeSessionId
        if (activeSessionId == null && sessions.isEmpty()) newSession()
        status = when {
            models.isEmpty() -> "No models — open Models tab, Import GGUF"
            activeModelId == null -> "Models ready — Activate one"
            else -> "Registered: ${activeModel?.displayName ?: "?"} (weights not loaded)"
        }
    }

    fun setPane(p: AppPane) { pane = p }

    fun importGguf(uri: Uri) {
        if (busy) return
        busy = true
        status = "Importing…"
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = uri.lastPathSegment?.substringAfterLast('/')
                        ?.removeSuffix(".gguf")?.ifBlank { null }
                        ?: "model_${System.currentTimeMillis()}"
                    val dest = File(getApplication<Application>().filesDir, "models/$name.gguf")
                    dest.parentFile?.mkdirs()
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dest).use { output -> input.copyTo(output) }
                    } ?: error("open failed")
                    ModelEntry(
                        displayName = name,
                        filePath = dest.absolutePath,
                        sizeBytes = dest.length(),
                        sourceUri = uri.toString()
                    )
                }
            }
            result.onSuccess { entry ->
                models.add(entry)
                store.saveModels(models.toList())
                status = "Imported ${entry.displayName} (${entry.sizeBytes / 1024 / 1024} MB)"
                pane = AppPane.MODELS
            }.onFailure { status = "Import failed: ${it.message}" }
            busy = false
        }
    }

    fun removeModel(id: String) {
        if (activeModelId == id && weightsInRam) unloadWeights()
        val m = models.find { it.id == id } ?: return
        models.removeAll { it.id == id }
        store.saveModels(models.toList())
        if (activeModelId == id) {
            activeModelId = null
            store.activeModelId = null
        }
        runCatching { File(m.filePath).delete() }
        status = "Removed ${m.displayName}"
    }

    fun activateModel(id: String) {
        val m = models.find { it.id == id } ?: return
        if (busy) return
        busy = true
        status = "Loading ${m.displayName} into RAM…"
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (weightsInRam) LlamaBridge.nativeUnload()
            }
            val ok = withContext(Dispatchers.IO) {
                LlamaBridge.loadFromFile(
                    m.filePath,
                    LlamaBridge.LoadConfig(
                        nThreads = nThreads,
                        nCtx = nCtx,
                        useMmap = useMmap,
                        useMlock = useMlock,
                        nice = nice,
                        affinityMask = affinityMask
                    )
                ).isSuccess
            }
            if (ok) {
                activeModelId = id
                store.activeModelId = id
                weightsInRam = true
                status = "ACTIVE · ${m.displayName} · ${m.sizeBytes / 1024 / 1024} MB · ctx=$nCtx"
            } else {
                weightsInRam = false
                status = "Load failed for ${m.displayName}"
            }
            busy = false
        }
    }

    fun unloadWeights() {
        viewModelScope.launch(Dispatchers.IO) {
            LlamaBridge.nativeUnload()
            withContext(Dispatchers.Main) {
                weightsInRam = false
                status = "Weights unloaded · ${activeModel?.displayName ?: "none"} still registered"
            }
        }
    }

    fun newSession() {
        val s = Session(title = "Session ${sessions.size + 1}", modelId = activeModelId)
        sessions.add(0, s)
        activeSessionId = s.id
        store.activeSessionId = s.id
        persistSessions()
        pane = AppPane.LAB
    }

    fun openSession(id: String) {
        activeSessionId = id
        store.activeSessionId = id
        pane = AppPane.LAB
    }

    fun deleteSession(id: String) {
        sessions.removeAll { it.id == id }
        if (activeSessionId == id) {
            activeSessionId = sessions.firstOrNull()?.id
            store.activeSessionId = activeSessionId
        }
        persistSessions()
    }

    fun renameSession(id: String, title: String) {
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        sessions[i] = sessions[i].copy(title = title, updatedAt = System.currentTimeMillis())
        persistSessions()
    }

    private fun persistSessions() = store.saveSessions(sessions.toList())

    private fun mutateActiveSession(block: (Session) -> Session) {
        val id = activeSessionId ?: return
        val i = sessions.indexOfFirst { it.id == id }
        if (i < 0) return
        sessions[i] = block(sessions[i]).copy(updatedAt = System.currentTimeMillis())
        persistSessions()
    }

    fun addMemory(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        memoryNotes.add(0, MemoryNote(text = t))
        store.saveMemory(memoryNotes.toList())
    }

    fun removeMemory(id: String) {
        memoryNotes.removeAll { it.id == id }
        store.saveMemory(memoryNotes.toList())
    }

    private fun memoryPrefix(): String {
        if (memoryNotes.isEmpty()) return ""
        return buildString {
            appendLine("[memory]")
            memoryNotes.take(12).forEach { appendLine("- ${it.text}") }
            appendLine("[/memory]")
            appendLine()
        }
    }

    fun send(text: String) {
        if (!weightsInRam || busy || text.isBlank()) return
        if (activeSessionId == null) newSession()
        mutateActiveSession { it.copy(messages = it.messages + ChatMessage(ChatMessage.Role.USER, text.trim())) }
        val sess = activeSession
        if (sess != null && sess.messages.count { it.role == ChatMessage.Role.USER } <= 1) {
            renameSession(sess.id, text.trim().take(40))
        }
        busy = true
        status = "Generating…"
        contextUsedApprox = (activeSession?.messages?.sumOf { it.text.length } ?: 0) / 4
        viewModelScope.launch {
            val prompt = memoryPrefix() + text.trim()
            val out = withContext(Dispatchers.IO) {
                LlamaBridge.nativeGenerate(prompt, maxTokens, temperature, topK, 0.9f)
            }
            mutateActiveSession {
                it.copy(messages = it.messages + ChatMessage(ChatMessage.Role.ASSISTANT, out))
            }
            status = if (weightsInRam) "ACTIVE · ${activeModel?.displayName}" else "Idle"
            busy = false
        }
    }

    fun bench() {
        if (!weightsInRam || busy) return
        busy = true
        status = "Benchmark…"
        viewModelScope.launch {
            val out = withContext(Dispatchers.IO) {
                LlamaBridge.nativeBench("The meaning of life is", maxTokens)
            }
            mutateActiveSession {
                it.copy(messages = it.messages + ChatMessage(ChatMessage.Role.METRICS, out))
            }
            status = "Bench done"
            busy = false
        }
    }

    fun abort() {
        LlamaBridge.nativeAbort()
        status = "Abort requested"
    }

    override fun onCleared() {
        LlamaBridge.nativeUnload()
        LlamaBridge.nativeShutdown()
        super.onCleared()
    }
}
