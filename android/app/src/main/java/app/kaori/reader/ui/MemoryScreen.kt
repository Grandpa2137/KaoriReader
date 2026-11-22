package app.kaori.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kaori.reader.data.ChatStore
import app.kaori.reader.data.Episode
import app.kaori.reader.data.KaoriEngine
import app.kaori.reader.data.LlmConfig
import app.kaori.reader.data.MemoryStore
import app.kaori.reader.data.Persona
import app.kaori.reader.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * „Co ona o mnie wie" — przeglądarka pamięci.
 *
 * Bez tego ekranu nikt nie zaufa pamięci trzymanej na telefonie: musi być widać
 * każdy fakt, dać się go usunąć i wyczyścić wszystko jednym przyciskiem.
 */
@Composable
fun MemoryScreen(
    persona: Persona,
    apiKey: String,
    config: LlmConfig,
    threadId: String,
    onClose: () -> Unit,
    onChange: () -> Unit,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(0) }
    var rev by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }

    val facts = remember(rev, persona.id) { MemoryStore.facts(ctx, persona.id) }
    val threads = remember(rev, persona.id) { MemoryStore.threads(ctx, persona.id) }
    val episodes = remember(rev, persona.id) { MemoryStore.episodes(ctx, persona.id) }

    fun fmt(ts: Long): String =
        SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ts))

    Column(Modifier.fillMaxSize().background(p.uiBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", color = p.textMuted, fontSize = 20.sp, modifier = Modifier.clickable { onClose() })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("What ${persona.name} remembers", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("wszystko lokalnie, na tym telefonie", color = p.textDim, fontSize = 9.sp)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            listOf(
                "Fakty ${facts.size}",
                "Threads ${threads.size}",
                "Streszczenia ${episodes.size}",
            ).forEachIndexed { i, label ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (tab == i) p.accent.copy(alpha = 0.20f) else p.uiSurface)
                        .clickable { tab = i }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        label,
                        color = if (tab == i) p.accent else p.textMuted,
                        fontSize = 9.5.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        ) {
            when (tab) {
                0 -> items(facts.size) { i ->
                    val f = facts[i]
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                f.key.uppercase(),
                                color = p.textDim,
                                fontSize = 8.5.sp,
                                letterSpacing = 0.8.sp,
                            )
                            Text(f.value, color = p.text, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                        Text(
                            "✕",
                            color = p.textMuted,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable {
                                    MemoryStore.deleteFact(ctx, persona.id, f.key)
                                    rev++
                                    onChange()
                                }
                                .padding(8.dp),
                        )
                    }
                }
                1 -> items(threads.size) { i ->
                    val t = threads[i]
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.topic, color = p.text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                            Text(t.state, color = p.textMuted, fontSize = 11.sp, lineHeight = 15.sp)
                            Text(fmt(t.at), color = p.textDim, fontSize = 8.5.sp)
                        }
                        Text(
                            "✕",
                            color = p.textMuted,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable {
                                    MemoryStore.deleteThread(ctx, persona.id, t.topic)
                                    rev++
                                    onChange()
                                }
                                .padding(8.dp),
                        )
                    }
                }
                else -> items(episodes.size) { i ->
                    val e = episodes[i]
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${fmt(e.at)} · importance ${e.importance}",
                                color = p.textDim,
                                fontSize = 8.5.sp,
                            )
                            Text(
                                e.summary,
                                color = p.text,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            "✕",
                            color = p.textMuted,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable {
                                    MemoryStore.deleteEpisode(ctx, persona.id, e.at)
                                    rev++
                                    onChange()
                                }
                                .padding(8.dp),
                        )
                    }
                }
            }
        }

        msg?.let {
            Text(
                it,
                color = p.accent,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            // Ręczny zapis: czasem chce się zapamiętać rozmowę od razu, bez czekania
            // na próg dwunastu wiadomości.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (busy) p.uiSurfaceRaised else p.accent.copy(alpha = 0.16f))
                    .clickable(enabled = !busy) {
                        busy = true
                        msg = null
                        scope.launch {
                            val msgs = withContext(Dispatchers.IO) { ChatStore.load(ctx, threadId) }
                            val res = KaoriEngine.extractMemory(apiKey, config, persona.name, msgs, minMessages = 2)
                            res.onSuccess { e ->
                                withContext(Dispatchers.IO) {
                                    val pid = persona.id
                                    if (e.summary.isNotBlank()) {
                                        MemoryStore.addEpisode(
                                            ctx, pid,
                                            Episode(e.summary, System.currentTimeMillis(), e.importance, e.keys),
                                        )
                                    }
                                    e.facts.forEach { (k, v) -> MemoryStore.mergeFact(ctx, pid, k, v) }
                                    e.threads.forEach { (t, s) -> MemoryStore.mergeThread(ctx, pid, t, s) }
                                }
                                msg = "saved: ${e.facts.size} facts, ${e.threads.size} threads"
                            }.onFailure { err -> msg = err.message ?: "couldn't save" }
                            busy = false
                            rev++
                            onChange()
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (busy) "reading the conversation…" else "Save this conversation to memory",
                    color = if (busy) p.textDim else p.accent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.uiSurface)
                    .clickable { confirmWipe = true }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Erase all memory", color = androidx.compose.ui.graphics.Color(0xFFE08A9A), fontSize = 11.sp)
            }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            containerColor = p.uiBg,
            title = { Text("Erase all memory?", fontSize = 15.sp) },
            text = {
                Text(
                    "All facts, threads and summaries of this persona will be gone. " +
                        "Chat history and books stay.",
                    color = p.textMuted,
                    fontSize = 11.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    MemoryStore.wipe(ctx, persona.id)
                    rev++
                    onChange()
                }) { Text("Erase") }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }
}
