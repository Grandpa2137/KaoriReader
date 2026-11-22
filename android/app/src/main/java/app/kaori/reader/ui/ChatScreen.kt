package app.kaori.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kaori.reader.data.ChatMessage
import app.kaori.reader.data.ChatStore
import app.kaori.reader.data.Images
import app.kaori.reader.data.Episode
import app.kaori.reader.data.MemoryStore
import app.kaori.reader.data.KaoriEngine
import app.kaori.reader.ui.theme.KaoriPalette
import app.kaori.reader.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(
    threadId: String,
    bookTitle: String?,
    apiKey: String,
    visibleText: () -> String,
    visibleImages: () -> List<String> = { emptyList() },
    /** Krótki opis, ile książki Kaori faktycznie widzi (plakietka w nagłówku). */
    contextInfo: String = "",
    config: app.kaori.reader.data.LlmConfig = app.kaori.reader.data.LlmConfig(),
    persona: app.kaori.reader.data.Persona = app.kaori.reader.data.PersonaStore.KAORI,
    strictRole: Boolean = true,
    onOpenSettings: () -> Unit,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Wbudowana persona siedzi w assets — jeden plik, który można skopiować do edytora.
    val personaText = remember { KaoriEngine.builtinPersonaText(ctx) }

    val messages = remember(threadId) { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingImage by remember { mutableStateOf<java.io.File?>(null) }
    var viewer by remember { mutableStateOf<String?>(null) }
    var showContext by remember { mutableStateOf(false) }

    // `send` musi być zadeklarowane przed launcher'ami, bo one je wywołują.
    fun send(text: String) {
        val q = text.trim()
        val image = pendingImage
        if ((q.isEmpty() && image == null) || busy) return
        input = ""
        pendingImage = null
        messages += ChatMessage("user", q, image?.absolutePath, System.currentTimeMillis())
        busy = true
        // zapis od razu — jak apka padnie w trakcie odpowiedzi, wiadomość nie ginie
        scope.launch { withContext(Dispatchers.IO) { ChatStore.save(ctx, threadId, messages.toList()) } }
        scope.launch {
            val history = messages.dropLast(1).toList()
            val base64 = image?.let { withContext(Dispatchers.IO) { Images.toBase64(it) } }
            val memories = withContext(Dispatchers.IO) {
                // Memory trzymana wyłącznie na tym telefonie: fakty, wątki, streszczenia.
                MemoryStore.recall(ctx, persona.id, q)
            }
            // Link w wiadomości → Kaori go czyta. Puste, gdy nie ma linku albo strona padła.
            val link = KaoriEngine.firstUrl(q)
            val webText = if (link != null) {
                withContext(Dispatchers.IO) { KaoriEngine.fetchPage(link) }
            } else ""
            // Ilustracje z książki — Kaori ma je widzieć, nie tylko czytać tekst.
            val bookImages = withContext(Dispatchers.IO) {
                visibleImages().mapNotNull { path ->
                    runCatching { Images.toBase64(java.io.File(path), 1024) }.getOrNull()
                }
            }
            val answer = KaoriEngine.reply(
                apiKey = apiKey,
                config = config,
                persona = persona,
                strictRole = strictRole,
                personaText = personaText,
                bookTitle = bookTitle,
                visibleText = visibleText(),
                history = history,
                question = q.ifBlank { "(photo)" },
                imageBase64 = base64,
                memories = memories,
                webText = webText,
                bookImages = bookImages,
            )
            // Historia czatu leży lokalnie (ChatStore) i nie jest nigdzie wysyłana
            // poza wywołanie modelu, które inicjuje użytkownik.
            // kończyło się tym, że cytowała sama siebie i dopowiadała nieistniejące fakty.
            // Historia czatu i tak leży lokalnie (ChatStore).
            // Kilka krótkich bąbelków zamiast jednego akapitu — tak się pisze w czacie.
            val parts = splitBursts(answer)
            parts.forEachIndexed { i, part ->
                if (i > 0) kotlinx.coroutines.delay(kotlin.random.Random.nextLong(700L, 1700L))
                messages += ChatMessage("assistant", part, null, System.currentTimeMillis())
            }
            busy = false
            withContext(Dispatchers.IO) { ChatStore.save(ctx, threadId, messages.toList()) }

            // Co 12 wiadomości zapisujemy do pamięci STRESZCZENIE, a dodatkowo
            // FAKTY i WĄTKI — bez nich nie pamięta grafiku ani pracy.
            if (messages.size % 12 == 0) {
                val extract = KaoriEngine.extractMemory(apiKey, config, persona.name, messages.toList())
                extract.onSuccess { e ->
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
                }
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            pendingImage = withContext(Dispatchers.IO) { Images.save(ctx, uri, threadId) }
        }
    }
    val pickPhoto: () -> Unit = {
        photoPicker.launch(
            androidx.activity.result.PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }

    // Głos: rozpoznawanie mowy systemu (offline), wynik od razu idzie do Kaori.
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val spoken = res.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) send(spoken)
    }
    val askMicPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) voiceLauncher.launch(voiceIntent()) }
    val startVoice: () -> Unit = {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) voiceLauncher.launch(voiceIntent())
        else askMicPermission.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(threadId) {
        val loaded = withContext(Dispatchers.IO) { ChatStore.load(ctx, threadId) }
        messages.clear()
        messages.addAll(loaded)
    }

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().background(p.readerBg).imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(p.gradFrom, p.gradTo))),
                contentAlignment = Alignment.Center,
            ) { Text(persona.emoji, fontSize = 15.sp) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(persona.name, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    bookTitle?.take(34) ?: "just talking",
                    color = p.textDim,
                    fontSize = 9.sp,
                )
                if (bookTitle != null && contextInfo.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(p.accent.copy(alpha = 0.14f))
                            .clickable { showContext = true }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            contextInfo,
                            color = p.accent,
                            fontSize = 8.5.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (messages.isNotEmpty()) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(p.uiSurface)
                        .clickable {
                            // najpierw czyścimy ekran, streszczenie leci w tle —
                            // inaczej czat wisiał, czekając na sieć
                            val snapshot = messages.toList()
                            messages.clear()
                            scope.launch {
                                withContext(Dispatchers.IO) { ChatStore.clear(ctx, threadId) }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("clear", color = p.textMuted, fontSize = 10.sp)
                }
                Spacer(Modifier.width(6.dp))
            }
            if (apiKey.isBlank()) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(p.accent.copy(alpha = 0.16f))
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("API key", color = p.accent, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        if (messages.isEmpty()) {
            Column(
                Modifier.weight(1f).padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (bookTitle == null) "So, what's up" else "Ask about what you have already read.",
                    color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (bookTitle == null) "No book, no script. Just talking."
                    else "Not spoiling it — I only see the text up to where you are.",
                    color = p.textDim, fontSize = 11.sp, lineHeight = 17.sp,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(messages) { i, m ->
                    val prev = messages.getOrNull(i - 1)
                    if (prev == null || !sameDay(prev.ts, m.ts)) {
                        DayHeader(m.ts, p)
                    }
                    Bubble(m, p) { viewer = it }
                }
                if (busy) {
                    item { Typing(p) }
                }
            }
        }

        pendingImage?.let { img ->
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val bmp = remember(img) {
                    android.graphics.BitmapFactory.decodeFile(img.absolutePath)
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(9.dp))
                }
                Text("photo ready to send", color = p.textDim, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "delete",
                    color = p.accent,
                    fontSize = 10.sp,
                    modifier = Modifier.clickable { pendingImage = null },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.uiSurfaceRaised)
                    .clickable { pickPhoto() },
                contentAlignment = Alignment.Center,
            ) { Text("+", color = p.textMuted, fontSize = 20.sp) }
            Spacer(Modifier.width(7.dp))
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.uiSurfaceRaised)
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (input.isEmpty()) {
                    Text("write to ${persona.name}…", color = p.textDim, fontSize = 12.sp)
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = TextStyle(color = p.text, fontSize = 12.sp),
                    cursorBrush = Brush.verticalGradient(listOf(p.accent, p.accent)),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(7.dp))
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.uiSurfaceRaised)
                    .clickable { startVoice() },
                contentAlignment = Alignment.Center,
            ) { Text("🎤", fontSize = 15.sp) }
            Spacer(Modifier.width(7.dp))
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (busy || input.isBlank()) Modifier.background(p.uiSurface)
                        else Modifier.background(Brush.linearGradient(listOf(p.gradFrom, p.gradTo)))
                    )
                    .clickable { send(input) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (busy) "…" else "↑",
                    color = if (busy || input.isBlank()) p.textDim else Color.White,
                    fontSize = 15.sp,
                )
            }
        }

        // Podgląd zdjęcia na pełnym ekranie — tap zamyka.
        viewer?.let { path ->
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { viewer = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.94f))
                        .clickable { viewer = null },
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = remember(path) {
                        android.graphics.BitmapFactory.decodeFile(path)
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // „Co Kaori widzi" — dokładnie ten tekst idzie do modelu. Kto tego nie
        // może sprawdzić, ten nie uwierzy w blokadę spoilerów.
        if (showContext) {
            val shown = visibleText()
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showContext = false },
                containerColor = p.uiBg,
                title = { Text("Co Kaori widzi", fontSize = 15.sp) },
                text = {
                    Column {
                        Text(contextInfo, color = p.accent, fontSize = 10.sp)
                        Text(
                            "${shown.length} characters from this book",
                            color = p.textDim,
                            fontSize = 9.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(
                            Modifier
                                .heightIn(max = 380.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                if (shown.length > 2500) "…" + shown.takeLast(2500) else shown,
                                color = p.text,
                                fontSize = 10.sp,
                                lineHeight = 15.sp,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Showing the end of the text — what is on your screen right now.",
                            color = p.textDim,
                            fontSize = 8.5.sp,
                        )
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showContext = false }) {
                        Text("Close")
                    }
                },
            )
        }
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val f = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH)
    return f.format(java.util.Date(a)) == f.format(java.util.Date(b))
}

/** „23:41” today, „yesterday 23:41”, „12 lis 23:41” starsze. */
internal fun formatStamp(ts: Long): String {
    val now = System.currentTimeMillis()
    val f = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH)
    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.ENGLISH).format(java.util.Date(ts))
    return when {
        f.format(java.util.Date(ts)) == f.format(java.util.Date(now)) -> time
        f.format(java.util.Date(ts + 86_400_000L)) == f.format(java.util.Date(now)) -> "yesterday $time"
        else -> java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale("pl", "PL")).format(java.util.Date(ts))
    }
}

@Composable
private fun DayHeader(ts: Long, p: KaoriPalette) {
    val now = System.currentTimeMillis()
    val f = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH)
    val label = when {
        f.format(java.util.Date(ts)) == f.format(java.util.Date(now)) -> "today"
        f.format(java.util.Date(ts + 86_400_000L)) == f.format(java.util.Date(now)) -> "yesterday"
        else -> java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("pl", "PL")).format(java.util.Date(ts))
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(p.uiSurface)
                .padding(horizontal = 11.dp, vertical = 4.dp)
        ) {
            Text(label, color = p.textDim, fontSize = 9.sp)
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, p: KaoriPalette, onImage: (String) -> Unit) {
    val fromKaori = m.role == "assistant"
    Row(Modifier.fillMaxWidth()) {
        if (!fromKaori) Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (fromKaori) 4.dp else 14.dp,
                        bottomEnd = if (fromKaori) 14.dp else 4.dp,
                    )
                )
                .background(if (fromKaori) p.bubbleKaori else p.accent.copy(alpha = 0.22f))
                .padding(horizontal = 13.dp, vertical = 10.dp)
        ) {
            Column {
                m.imagePath?.let { path ->
                    val bmp = remember(path) { android.graphics.BitmapFactory.decodeFile(path) }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { onImage(path) },
                        )
                        if (m.content.isNotBlank()) Spacer(Modifier.height(8.dp))
                    }
                }
                if (m.content.isNotBlank()) {
                    Text(m.content, color = p.text, fontSize = 12.sp, lineHeight = 18.sp)
                }
                if (m.ts > 0) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        formatStamp(m.ts),
                        color = p.textDim,
                        fontSize = 8.sp,
                        modifier = Modifier.align(if (fromKaori) Alignment.Start else Alignment.End),
                    )
                }
            }
        }
        if (fromKaori) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Typing(p: KaoriPalette) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(p.bubbleKaori)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text("typing…", color = p.textDim, fontSize = 11.sp)
        }
        Spacer(Modifier.weight(1f))
    }
}


/** Intent rozpoznawania mowy — polski, dopasowany do gadania z Kaori. */
private fun voiceIntent(): android.content.Intent =
    android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak to Kaori…")
    }
