package com.falconbench.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.falconbench.app.BenchViewModel
import com.falconbench.app.data.AppPane
import com.falconbench.app.data.ChatMessage
import com.falconbench.app.data.MemoryNote
import com.falconbench.app.data.ModelEntry
import com.falconbench.app.data.Session

@Composable
fun BenchScreen(vm: BenchViewModel, onPickModel: () -> Unit) {
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavItem(AppPane.LAB, "Lab", Icons.Default.Science, vm)
                NavItem(AppPane.MODELS, "Models", Icons.Default.Storage, vm)
                NavItem(AppPane.HISTORY, "History", Icons.Default.History, vm)
                NavItem(AppPane.MEMORY, "Memory", Icons.Default.Psychology, vm)
                NavItem(AppPane.CONTROLS, "Runtime", Icons.Default.Tune, vm)
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            StatusStrip(vm)
            when (vm.pane) {
                AppPane.LAB -> LabPane(vm)
                AppPane.MODELS -> ModelsPane(vm, onPickModel)
                AppPane.HISTORY -> HistoryPane(vm)
                AppPane.MEMORY -> MemoryPane(vm)
                AppPane.CONTROLS -> ControlsPane(vm)
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    pane: AppPane,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    vm: BenchViewModel
) {
    NavigationBarItem(
        selected = vm.pane == pane,
        onClick = { vm.setPane(pane) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, fontSize = 10.sp) }
    )
}

@Composable
private fun StatusStrip(vm: BenchViewModel) {
    val active = vm.activeModel
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (vm.weightsInRam && active != null) "ACTIVE  ${active.displayName}"
                else if (active != null) "REGISTERED  ${active.displayName}  (not in RAM)"
                else "NO ACTIVE MODEL",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = if (vm.weightsInRam) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                vm.status,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (vm.weightsInRam) {
            TextButton(onClick = { vm.unloadWeights() }, enabled = !vm.busy) {
                Text("Unload", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun LabPane(vm: BenchViewModel) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val msgs = vm.labMessages
    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.lastIndex)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                vm.activeSession?.title ?: "No session",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                "ctx~${vm.contextUsedApprox}/${vm.nCtx}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = { vm.newSession() }) {
                Icon(Icons.Default.Add, contentDescription = "New session")
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(msgs) { MessageRow(it) }
        }
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .padding(10.dp),
                textStyle = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    if (input.isEmpty()) {
                        Text(
                            if (!vm.weightsInRam) "Activate a model first…" else "prompt",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                    inner()
                }
            )
            Spacer(Modifier.width(6.dp))
            FilledIconButton(
                onClick = { val t = input; input = ""; vm.send(t) },
                enabled = vm.weightsInRam && !vm.busy && input.isNotBlank()
            ) { Icon(Icons.Default.PlayArrow, null) }
            IconButton(onClick = { vm.bench() }, enabled = vm.weightsInRam && !vm.busy) {
                Icon(Icons.Default.Speed, null)
            }
            if (vm.busy) {
                IconButton(onClick = { vm.abort() }) { Icon(Icons.Default.Stop, null) }
            }
        }
    }
}

@Composable
private fun MessageRow(msg: ChatMessage) {
    val label = when (msg.role) {
        ChatMessage.Role.USER -> "you"
        ChatMessage.Role.ASSISTANT -> "model"
        ChatMessage.Role.METRICS -> "bench"
        ChatMessage.Role.SYSTEM -> "sys"
    }
    Column(Modifier.fillMaxWidth()) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        Text(
            msg.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(8.dp)
        )
    }
}

@Composable
private fun ModelsPane(vm: BenchViewModel, onPickModel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MODEL REGISTRY", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = onPickModel, enabled = !vm.busy) {
                Icon(Icons.Default.FileOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import GGUF")
            }
        }
        Text(
            "Only one model holds weights in RAM (32-bit). Others stay on disk.",
            fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        if (vm.models.isEmpty()) {
            Text("Empty. Import Falcon or any .gguf.", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.models, key = { it.id }) { ModelCard(it, vm) }
        }
    }
}

@Composable
private fun ModelCard(m: ModelEntry, vm: BenchViewModel) {
    val isActive = m.id == vm.activeModelId
    val inRam = isActive && vm.weightsInRam
    Column(
        Modifier.fillMaxWidth()
            .border(if (inRam) 2.dp else 1.dp, if (inRam) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(m.displayName, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        Text("${m.sizeBytes / 1024 / 1024} MB · ${m.filePath.substringAfterLast('/')}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            when {
                inRam -> "STATE  WEIGHTS IN RAM  ·  ACTIVE"
                isActive -> "STATE  SELECTED  ·  NOT LOADED"
                else -> "STATE  ON DISK"
            },
            fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            color = if (inRam) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!inRam) {
                Button(onClick = { vm.activateModel(m.id) }, enabled = !vm.busy) {
                    Text(if (isActive) "Load into RAM" else "Activate")
                }
            } else {
                OutlinedButton(onClick = { vm.unloadWeights() }) { Text("Unload") }
            }
            TextButton(onClick = { vm.removeModel(m.id) }) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun HistoryPane(vm: BenchViewModel) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SESSIONS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = { vm.newSession() }) { Text("New") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vm.sessions, key = { it.id }) { SessionRow(it, vm) }
        }
    }
}

@Composable
private fun SessionRow(s: Session, vm: BenchViewModel) {
    val selected = s.id == vm.activeSessionId
    Row(
        Modifier.fillMaxWidth()
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { vm.openSession(s.id) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.title, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
            Text("${s.messages.size} msgs · model=${s.modelId?.take(8) ?: "—"}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { vm.deleteSession(s.id) }) { Icon(Icons.Default.Delete, null) }
    }
}

@Composable
private fun MemoryPane(vm: BenchViewModel) {
    var draft by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("MEMORY NOTES", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text("Injected into each prompt as [memory]…[/memory]. Local only.", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)).padding(10.dp),
                textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.addMemory(draft); draft = "" }) { Text("Add") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vm.memoryNotes, key = { it.id }) { n: MemoryNote ->
                Row(
                    Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(n.text, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    IconButton(onClick = { vm.removeMemory(n.id) }) { Icon(Icons.Default.Close, null) }
                }
            }
        }
    }
}

@Composable
private fun ControlsPane(vm: BenchViewModel) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("RUNTIME  ·  nproc=${vm.nproc}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text("Applied on next Activate / Load into RAM.", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SliderRow("threads", vm.nThreads.toFloat(), 1f..vm.nproc.coerceAtLeast(1).toFloat()) { vm.nThreads = it.toInt() }
        SliderRow("ctx", vm.nCtx.toFloat(), 512f..4096f) { vm.nCtx = (it / 256f).toInt().coerceAtLeast(1) * 256 }
        SliderRow("temp", vm.temperature, 0f..1.5f) { vm.temperature = it }
        SliderRow("tokens", vm.maxTokens.toFloat(), 16f..512f) { vm.maxTokens = it.toInt() }
        SliderRow("nice", vm.nice.toFloat(), -10f..10f) { vm.nice = it.toInt() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = vm.useMmap, onClick = { vm.useMmap = !vm.useMmap }, label = { Text("mmap") })
            FilterChip(selected = vm.useMlock, onClick = { vm.useMlock = !vm.useMlock }, label = { Text("mlock") })
            FilterChip(
                selected = vm.affinityMask == 0xFL,
                onClick = { vm.affinityMask = if (vm.affinityMask == 0xFL) 0L else 0xFL },
                label = { Text("cpu0-3") }
            )
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label ${if (label == "temp") "%.1f".format(value) else value.toInt()}",
            Modifier.width(100.dp), fontFamily = FontFamily.Monospace, fontSize = 12.sp
        )
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}
