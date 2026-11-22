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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kaori.reader.data.KaoriEngine
import app.kaori.reader.data.LlmConfig
import app.kaori.reader.data.Persona
import app.kaori.reader.data.PersonaStore
import app.kaori.reader.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lista postaci i edytor własnej.
 *
 * Kaori jest wbudowana i nieedytowalna (jej tekst to 8 kB promptu w silniku),
 * ale można ją skopiować jako punkt wyjścia dla własnej.
 */
@Composable
fun PersonaScreen(
    apiKey: String,
    config: LlmConfig,
    onClose: () -> Unit,
    onChange: () -> Unit,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var all by remember { mutableStateOf(PersonaStore.load(ctx)) }
    var activeId by remember { mutableStateOf(PersonaStore.activeId(ctx)) }
    var editing by remember { mutableStateOf<Persona?>(null) }

    // Systemowy „wstecz”: edytor → lista, lista → Ustawienia. Bez tego Android
    // minimalizował całą aplikację i wyglądało to jak zawieszenie.
    androidx.activity.compose.BackHandler {
        if (editing != null) editing = null else onClose()
    }

    fun reload() {
        all = PersonaStore.load(ctx)
        activeId = PersonaStore.activeId(ctx)
        onChange()
    }

    val current = editing
    if (current != null) {
        PersonaEditor(
            persona = current,
            apiKey = apiKey,
            config = config,
            onCancel = { editing = null },
            onSave = { saved ->
                PersonaStore.upsert(ctx, saved)
                editing = null
                reload()
            },
            onDelete = {
                PersonaStore.delete(ctx, current.id)
                editing = null
                reload()
            },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(p.uiBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", color = p.textMuted, fontSize = 20.sp, modifier = Modifier.clickable { onClose() })
            Spacer(Modifier.width(12.dp))
            Text("Personas", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "Each persona has its own memory. Books, progress and bookmarks are shared.",
            color = p.textDim,
            fontSize = 10.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(14.dp))

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(all.size) { i ->
                val persona = all[i]
                val on = persona.id == activeId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) p.accent.copy(alpha = 0.14f) else p.uiSurface)
                        .clickable {
                            PersonaStore.setActive(ctx, persona.id)
                            reload()
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(persona.emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(persona.name, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (persona.builtin) "built-in · read-only"
                            else "custom · language: ${persona.language}",
                            color = p.textDim,
                            fontSize = 9.5.sp,
                        )
                    }
                    if (on) Text("active", color = p.accent, fontSize = 9.sp)
                }
            }
            item {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(p.uiSurface)
                        .clickable {
                            editing = Persona(
                                id = PersonaStore.newId(),
                                name = "New persona",
                                emoji = "🌙",
                                how = "",
                                rules = PersonaStore.TEMPLATE,
                            )
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("+ your own persona", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(p.uiSurface)
                        .clickable {
                            // Prawdziwa kopia: cały prompt Kaori z assets trafia do pola
                            // „rules”, więc można go skrócić, przepisać albo wyrzucić połowę.
                            editing = Persona(
                                id = PersonaStore.newId(),
                                name = "My Kaori",
                                emoji = "🎀",
                                how = "",
                                rules = KaoriEngine.builtinPersonaText(ctx),
                            )
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Duplicate Kaori", color = p.textMuted, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun PersonaEditor(
    persona: Persona,
    apiKey: String,
    config: LlmConfig,
    onCancel: () -> Unit,
    onSave: (Persona) -> Unit,
    onDelete: () -> Unit,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(persona.name) }
    var emoji by remember { mutableStateOf(persona.emoji) }
    var language by remember { mutableStateOf(persona.language) }
    var how by remember { mutableStateOf(persona.how) }
    var rules by remember { mutableStateOf(persona.rules.ifBlank { PersonaStore.TEMPLATE }) }

    var idea by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(p.uiBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", color = p.textMuted, fontSize = 20.sp, modifier = Modifier.clickable { onCancel() })
            Spacer(Modifier.width(12.dp))
            Text("Persona", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                "Save",
                color = p.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable {
                        onSave(
                            persona.copy(
                                name = name.trim().ifBlank { "Persona" },
                                emoji = emoji.take(2).ifBlank { "🌙" },
                                language = language,
                                how = how.trim(),
                                rules = rules.trim(),
                            )
                        )
                    }
                    .padding(6.dp),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 26.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Name", color = p.textDim, fontSize = 9.sp, letterSpacing = 1.sp)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("EMOJI", color = p.textDim, fontSize = 9.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("🌙", "🎀", "🪶", "🍵", "✦", "🐈").forEach { e ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (emoji == e) p.accent.copy(alpha = 0.22f) else p.uiSurface)
                            .clickable { emoji = e }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) { Text(e, fontSize = 14.sp) }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("LANGUAGE", color = p.textDim, fontSize = 9.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto" to "same as them", "en" to "EN", "pl" to "PL", "ja" to "日本語").forEach { (id, label) ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (language == id) p.accent.copy(alpha = 0.22f) else p.uiSurface)
                            .clickable { language = id }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Text(
                            label,
                            color = if (language == id) p.accent else p.textMuted,
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("BUILD FROM DESCRIPTION", color = p.textDim, fontSize = 9.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Describe her in two sentences — I\'ll fill in \"how she talks\" and \"rules\". " +
                    "One model call on your own key.",
                color = p.textDim,
                fontSize = 9.sp,
                lineHeight = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = idea,
                onValueChange = { idea = it },
                placeholder = { Text("e.g. quiet, ironic, likes reading with me at night", fontSize = 11.sp) },
                textStyle = TextStyle(fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth().height(78.dp),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (busy) p.uiSurfaceRaised else p.accent.copy(alpha = 0.18f))
                    .clickable(enabled = !busy && idea.trim().length >= 8) {
                        busy = true
                        error = null
                        scope.launch {
                            if (apiKey.isBlank()) {
                                // Bez klucza nie ma sieci — kreator składa personę z szablonu.
                                val lower = idea.lowercase()
                                val tone = when {
                                    listOf("ironi", "dry", "sarkaz", "suchar").any { it in lower } -> "dry"
                                    listOf("ciep", "warm", "mił", "mil", "kind").any { it in lower } -> "warm"
                                    listOf("chaot", "energ", "loud", "szalon").any { it in lower } -> "chaotic"
                                    else -> "calm"
                                }
                                // tylko konkretne hobby — „noc" czy „reading" w zdaniu to nie hobby samo w sobie
                                val hobby = listOf(
                                    "gr" to "games", "game" to "games", "gier" to "games",
                                    "muzyk" to "music", "music" to "music",
                                    "czyt" to "reading", "read" to "reading",
                                    "film" to "films", "anime" to "anime", "mang" to "manga",
                                ).firstOrNull { (needle, _) -> needle in lower }?.second.orEmpty()
                                    .ifBlank {
                                        // „czytanie nocą" → to jest hobby, ale niech brzmi jak hobby
                                        if ("noc" in lower || "night" in lower) "late-night reading" else ""
                                    }
                                val built = PersonaStore.buildFromChips(
                                    name.ifBlank { "Kaori" },
                                    tone,
                                    hobby,
                                )
                                how = built.first
                                rules = built.second
                                error = "built from a template — add an API key for a model-written version"
                                busy = false
                            } else {
                                val res = KaoriEngine.buildPersona(apiKey, config, idea.trim())
                                res.onSuccess { built ->
                                    how = built.first
                                    rules = built.second.ifBlank { PersonaStore.TEMPLATE }
                                }.onFailure { e -> error = e.message ?: "Failed" }
                                busy = false
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (busy) "building…" else "Fill from description",
                    color = if (busy) p.textDim else p.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = androidx.compose.ui.graphics.Color(0xFFE08A9A), fontSize = 10.sp)
            }

            Spacer(Modifier.height(16.dp))
            Text("HOW SHE TALKS", color = p.textDim, fontSize = 9.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = how,
                onValueChange = { how = it },
                textStyle = TextStyle(fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth().height(96.dp),
            )

            Spacer(Modifier.height(14.dp))
            Text("RULES", color = p.textDim, fontSize = 9.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = rules,
                onValueChange = { rules = it },
                textStyle = TextStyle(fontSize = 11.sp),
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )

            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(p.uiSurface)
                    .clickable { confirmDelete = true }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Delete persona", color = androidx.compose.ui.graphics.Color(0xFFE08A9A), fontSize = 11.5.sp) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = p.uiBg,
            title = { Text("Delete this persona?", fontSize = 15.sp) },
            text = { Text("Her memory goes too. Books stay.", color = p.textMuted, fontSize = 11.sp) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
