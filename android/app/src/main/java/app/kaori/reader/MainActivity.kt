package app.kaori.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kaori.reader.data.AppSettings
import app.kaori.reader.data.Backup
import app.kaori.reader.data.Book
import app.kaori.reader.data.BookMeta
import app.kaori.reader.data.Collection
import app.kaori.reader.data.CollectionsStore
import app.kaori.reader.data.EpubParser
import app.kaori.reader.data.FolderImport
import app.kaori.reader.data.LibraryStore
import app.kaori.reader.data.LlmConfig
import app.kaori.reader.data.PersonaStore
import app.kaori.reader.data.ProgressStore
import app.kaori.reader.data.ReadRanges
import app.kaori.reader.data.ReadingProgress
import app.kaori.reader.data.SettingsStore
import app.kaori.reader.data.StatsStore
import app.kaori.reader.data.DayStat
import app.kaori.reader.ui.ChatScreen
import app.kaori.reader.ui.LibraryScreen
import app.kaori.reader.ui.MemoryScreen
import app.kaori.reader.ui.PersonaScreen
import app.kaori.reader.ui.ReaderScreen
import app.kaori.reader.ui.SettingsScreen
import app.kaori.reader.ui.pinAppShortcut
import app.kaori.reader.ui.theme.KaoriPalette
import app.kaori.reader.ui.theme.KaoriTheme
import app.kaori.reader.ui.theme.LocalPalette
import app.kaori.reader.ui.theme.fontById
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KaoriApp() }
    }
}

@Composable
private fun KaoriApp() {
    val ctx = LocalContext.current
    val store = remember { SettingsStore(ctx) }
    val scope = rememberCoroutineScope()
    val settings by store.settings.collectAsState(initial = AppSettings())

    var books by remember { mutableStateOf<List<BookMeta>>(emptyList()) }
    var current by remember { mutableStateOf<Book?>(null) }
    var chapter by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(0) }
    var inReader by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Map<String, ReadingProgress>>(emptyMap()) }
    var collections by remember { mutableStateOf<List<Collection>>(emptyList()) }
    var chatThread by remember { mutableStateOf("free") }
    // Czat otwarty prosto z czytnika — cofnięcie ma wtedy wrócić do książki,
    // a nie wypluć do biblioteki.
    var chatFromReader by remember { mutableStateOf(false) }
    val scrollSaveAt = remember { mutableMapOf<String, Long>() }
    // Skok z wyszukiwania w bibliotece — trafienie ma się otworzyć dokładnie tam.
    var pendingSeek by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    // Postacie: lista + edytor jako osobny ekran.
    var showPersonas by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }
    var personaTick by remember { mutableStateOf(0) }
    val persona = remember(personaTick) { PersonaStore.active(ctx) }
    val llm = LlmConfig(settings.baseUrl, settings.model)

    // Systemowy „wstecz” musi cofać w aplikacji, a nie minimalizować ją.
    androidx.activity.compose.BackHandler(enabled = !showPersonas && !showMemory && inReader) {
        inReader = false
    }
    androidx.activity.compose.BackHandler(
        enabled = !showPersonas && !showMemory && !inReader && tab != 0
    ) {
        tab = 0
    }

    /**
     * Tekst, który Kaori wolno znać: tylko to, co użytkownik NAPRAWDĘ przeczytał.
     *
     * Wcześniej szedł cały rozdział plus all poprzednie — po skoku na koniec
     * książki Kaori znała zakończenie. Teraz decydują przeczytane zakresy;
     * luki (pages tylko przewinięte) zostają dla niej nieznane.
     * Wyłączenie blokady w Settingsch daje jej całą książkę.
     */
    fun visibleText(): String {
        val book = current ?: return ""
        if (book.chapters.isEmpty()) return ""
        if (!settings.spoilerGuard) {
            val sb = StringBuilder()
            book.chapters.forEach { sb.append(it.text).append('\n') }
            return sb.toString()
        }
        val ranges = progress[book.id]?.read.orEmpty()
        if (ranges.isNotEmpty()) return ReadRanges.textOf(book, ranges)
        // Książka czytana przed wprowadzeniem zakresów — stare zachowanie.
        val idx = chapter.coerceIn(0, book.chapters.lastIndex)
        val sb = StringBuilder()
        for (i in 0..idx) sb.append(book.chapters[i].text).append('\n')
        return sb.toString()
    }

    /** Jednolinijkowa prawda o tym, ile książki Kaori widzi — do nagłówka czatu. */
    fun spoilerInfo(): String {
        val b = current ?: return ""
        if (b.chapters.isEmpty()) return ""
        if (!settings.spoilerGuard) return "guard off — she sees the whole book"
        val total = b.chapters.sumOf { it.text.length }.coerceAtLeast(1)
        val ranges = progress[b.id]?.read.orEmpty()
        if (ranges.isEmpty()) return "sees what is on your screen"
        val read = ranges.sumOf { it.last - it.first + 1 }.coerceAtMost(total)
        val pct = String.format(java.util.Locale.ENGLISH, "%.1f", read * 100f / total)
        val pages = progress[b.id]?.pages ?: 0
        // Pozycja liczona z NAJDALSZEGO przeczytanego znaku, nie z sumy — inaczej
        // dziury w środku zaniżałyby „do której pages doszła".
        val reach = ranges.maxOf { it.last }.coerceIn(0, total)
        val pageNow = if (pages > 0) (reach.toFloat() / total * pages).toInt().coerceAtLeast(1) else 0
        return if (pageNow > 0) {
            "sees $pct% of the book · latest ~p. $pageNow of $pages"
        } else {
            "sees $pct% of the book"
        }
    }

    /**
     * Migracja przy pierwszym otwarciu po aktualizacji: książka ma stary postęp,
     * a nie ma jeszcze zakresów. Zaznaczamy wszystko do dotychczasowej pozycji,
     * żeby Kaori nie straciła nagle tego, co już o książce wiedziała.
     */
    fun seedRead(b: Book) {
        val p = progress[b.id] ?: return
        if (p.read.isNotEmpty() || p.updated <= 0L) return
        val seeded = ReadRanges.seedTo(b, p.chapter)
        if (seeded.isEmpty()) return
        val entry = p.copy(read = seeded, updated = System.currentTimeMillis())
        progress = progress + (b.id to entry)
        scope.launch(Dispatchers.IO) { ProgressStore.save(ctx, b.id, entry) }
    }

    /** Ostatnie ilustracje z przeczytanej części — Kaori ma je zobaczyć. */
    fun visibleImages(max: Int = 2): List<String> {
        val book = current ?: return emptyList()
        if (book.chapters.isEmpty()) return emptyList()
        val idx = chapter.coerceIn(0, book.chapters.lastIndex)
        val all = ArrayList<String>()
        for (i in 0..idx) {
            book.chapters[i].images.sortedBy { it.offset }.forEach { all += it.path }
        }
        return all.takeLast(max)
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            LibraryStore.dedupe(ctx)
            books = LibraryStore.list(ctx)
            progress = ProgressStore.load(ctx)
            collections = CollectionsStore.load(ctx)
        }
        // Klucz można też wrzucić plikiem (files/api_key.txt) — wygodne przy testach.
        if (settings.apiKey.isBlank()) {
            val fromFile = withContext(Dispatchers.IO) {
                runCatching {
                    java.io.File(ctx.filesDir, "api_key.txt").takeIf { it.exists() }
                        ?.readText()?.trim().orEmpty()
                }.getOrDefault("")
            }
            if (fromFile.isNotBlank()) store.setApiKey(fromFile)
        }
    }

    /** Parsuje jeden plik i zapisuje go w bibliotece. */
    suspend fun importOne(uri: android.net.Uri): Book {
        val id = UUID.randomUUID().toString().replace("-", "").take(10)
        val src = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: ""
        val file = EpubParser.copyToStorage(ctx, uri, id)
        try {
            val book = EpubParser.parse(
                file, id,
                LibraryStore.coverFile(ctx, id),
                LibraryStore.imageDir(ctx, id),
            )
            LibraryStore.save(ctx, book, src)
            // Książka mogła czekać w kopii zapasowej wgranej wcześniej.
            Backup.applyPending(
                ctx,
                BookMeta(
                    id = book.id,
                    title = book.title,
                    author = book.author,
                    chapterCount = book.chapters.size,
                    chars = book.totalChars,
                    src = src,
                    cover = LibraryStore.coverFile(ctx, book.id).path,
                ),
            )
            return book
        } finally {
            // plik .epub jest już niepotrzebny, a waży kilkanaście MB
            file.delete()
        }
    }

    val backupSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = "Exporting backup…"
            val res = withContext(Dispatchers.IO) { runCatching { Backup.export(ctx, uri) } }
            importing = null
            res.onSuccess { r ->
                error = "Backup saved: ${r.progress} progress entries, ${r.collections} collections, ${r.bookmarks} bookmarks"
            }.onFailure { error = "Couldn't save the backup" }
        }
    }

    val backupOpen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = "Restoring backup…"
            val res = withContext(Dispatchers.IO) { runCatching { Backup.import(ctx, uri) } }
            if (res.isSuccess) {
                progress = withContext(Dispatchers.IO) { ProgressStore.load(ctx) }
                collections = withContext(Dispatchers.IO) { CollectionsStore.load(ctx) }
                books = withContext(Dispatchers.IO) { LibraryStore.list(ctx) }
            }
            importing = null
            res.onSuccess { r ->
                error = buildString {
                    append("Backup restored: ${r.progress} progress entries, ${r.collections} collections, ${r.bookmarks} bookmarks")
                    if (r.pending > 0) append(" · ${r.pending} waiting for that book")
                }
            }.onFailure { e -> error = e.message ?: "Couldn't restore the backup" }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = "Importing book…"
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { importOne(uri) }
            }
            result.onSuccess { b ->
                books = withContext(Dispatchers.IO) { LibraryStore.list(ctx) }
                current = b
                chapter = 0
                pendingSeek = null
                inReader = true
            }.onFailure { e ->
                error = e.message ?: "Couldn't import"
            }
            importing = null
        }
    }

    /**
     * Ponowny parse ISTNIEJĄCEJ książki — z tym samym id.
     *
     * importOne kasuje .epub po sparsowaniu, więc starsze książki nie mają
     * ilustracji. Tutaj parsujemy jeszcze raz z pliku użytkownika, ale pod to
     * samo id: postęp czytania i przynależność to collection zostają.
     */
    suspend fun refreshOne(uri: android.net.Uri, meta: BookMeta) {
        val file = EpubParser.copyToStorage(ctx, uri, meta.id)
        try {
            val book = EpubParser.parse(
                file, meta.id,
                LibraryStore.coverFile(ctx, meta.id),
                LibraryStore.imageDir(ctx, meta.id),
            )
            LibraryStore.save(ctx, book, meta.src)
            Backup.applyPending(ctx, meta)
        } finally {
            file.delete()
        }
    }

    /** Wspólna ścieżka: pobierz listę URI, zaimportuj, show wynik. */
    fun runImport(emptyMessage: String, producer: suspend () -> List<android.net.Uri>) {
        importing = "Scanning…"
        error = null
        scope.launch {
            val uris = withContext(Dispatchers.IO) { runCatching { producer() }.getOrDefault(emptyList()) }
            if (uris.isEmpty()) {
                importing = null
                error = emptyMessage
                return@launch
            }
            // Dopasowanie po nazwie pliku — tom 1 i tom 2 mają często identyczny dc:title.
            // WAŻNE: bierzemy indeks z dysku, bo stan `books` bywa jeszcze pusty
            // przy pierwszym uruchomieniu i wtedy wszystko wchodziło drugi raz.
            val byName = withContext(Dispatchers.IO) {
                LibraryStore.list(ctx).filter { it.src.isNotBlank() }.associateBy { it.src }
            }
            var added = 0
            var refreshed = 0
            var failed = 0
            uris.forEachIndexed { i, u ->
                importing = "Processing ${i + 1} / ${uris.size}"
                val name = u.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: ""
                val existing = byName[name]
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        if (existing != null) {
                            refreshOne(u, existing)   // ta sama książka, nowy parse
                            2
                        } else {
                            importOne(u)
                            1
                        }
                    }.getOrDefault(0)
                }
                when (result) {
                    1 -> added++
                    2 -> refreshed++
                    else -> failed++
                }
            }
            books = withContext(Dispatchers.IO) { LibraryStore.list(ctx) }
            importing = null
            error = when {
                added == 0 && refreshed == 0 -> "Nothing could be processed (errors: $failed)"
                else -> buildString {
                    if (added > 0) append("Added $added")
                    if (refreshed > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("refreshed $refreshed (illustrations)")
                    }
                    if (failed > 0) append(" · errors: $failed")
                }
            }
        }
    }

    // Wybór dowolnego folderu przez SAF (działa np. dla podfolderów Downloads).
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        runImport("No .epub files in this folder") {
            FolderImport.epubUris(ctx, tree)
        }    }

    // Ekran „Dostęp do wszystkich plików" — po powrocie skanujemy Downloads.
    val allFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Environment.isExternalStorageManager()) {
            runImport("No EPUBs in Downloads") { FolderImport.scanDownloads() }
        } else {
            error = "No file access, so I can't scan the folder"
        }
    }

    val onScanFolder: () -> Unit = {
        if (Environment.isExternalStorageManager()) {
            runImport("No EPUBs in Downloads") { FolderImport.scanDownloads() }
        } else {
            runCatching {
                allFilesLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${ctx.packageName}"),
                    )
                )
            }.onFailure {
                error = "Can't open permission settings"
            }
        }
    }

    val palette = SettingsStore.themeFor(settings.themeId)

    // Przycisk cofania w telefonie: najpierw wyjście z czytnika, potem powrót do biblioteki.
    // Dopiero gdy nie ma czego cofać, system minimalizuje aplikację.
    androidx.activity.compose.BackHandler(enabled = inReader || tab != 0) {
        if (inReader) {
            inReader = false
        } else if (tab != 0) {
            if (tab == 1 && chatFromReader && current != null) {
                chatFromReader = false
                tab = 0
                inReader = true
            } else {
                tab = 0
                chatThread = "free"
                chatFromReader = false
            }
        }
    }

    KaoriTheme(palette = palette) {
        val p = LocalPalette.current
        val font = fontById(settings.fontId)

        Box(Modifier.fillMaxSize().background(p.uiBg)) {
            when {
                showPersonas -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    PersonaScreen(
                        apiKey = settings.apiKey,
                        config = llm,
                        onClose = { showPersonas = false },
                        onChange = { personaTick++ },
                    )
                }
                showMemory -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    MemoryScreen(
                        persona = persona,
                        apiKey = settings.apiKey,
                        config = llm,
                        threadId = chatThread,
                        onClose = { showMemory = false },
                        onChange = { personaTick++ },
                    )
                }
                importing != null -> ImportingScreen(p, importing!!)
                inReader && current != null -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    ReaderScreen(
                        book = current!!,
                        chapterIndex = chapter.coerceIn(0, (current!!.chapters.size - 1).coerceAtLeast(0)),
                        font = font,
                        fontSize = settings.fontSize,
                        scrollMode = settings.scrollMode,
                        onScrollMode = { scope.launch { store.setScrollMode(it) } },
                        onChapter = { chapter = it },
                        onBack = { inReader = false },
                        onOpenChat = {
                            chatFromReader = true
                            inReader = false
                            chatThread = "book:${current?.id}"
                            tab = 1
                        },
                        onProgress = { c, addr, pages ->
                            val bookId = current!!.id
                            val prevEntry = progress[bookId]
                            val prev = prevEntry?.addr ?: addr
                            val entry = ReadingProgress(
                                c, addr, pages, System.currentTimeMillis(),
                                read = prevEntry?.read.orEmpty(),
                            )
                            progress = progress + (bookId to entry)
                            scope.launch(Dispatchers.IO) {
                                ProgressStore.save(ctx, bookId, entry)
                                if (addr > prev) StatsStore.addPages(ctx, addr - prev)
                            }
                        },
                        onReadingMinute = {
                            scope.launch(Dispatchers.IO) { StatsStore.addMinute(ctx) }
                        },
                        onCharOffset = { },
                        // Przeczytane kawałki — z nich budujemy tekst, który Kaori wolno znać.
                        onSeen = { start, end ->
                            val b = current
                            if (b != null) {
                                val prev = progress[b.id]
                                val before = prev?.read.orEmpty()
                                val merged = ReadRanges.add(before, start, end)
                                if (merged != before) {
                                    val entry = ReadingProgress(
                                        chapter = prev?.chapter ?: chapter,
                                        addr = prev?.addr ?: 0,
                                        pages = prev?.pages ?: 0,
                                        updated = System.currentTimeMillis(),
                                        scrollOffset = prev?.scrollOffset ?: 0,
                                        read = merged,
                                    )
                                    progress = progress + (b.id to entry)
                                    scope.launch(Dispatchers.IO) { ProgressStore.save(ctx, b.id, entry) }
                                }
                            }
                        },
                        // W trybie scroll nie ma stron, więc postęp liczymy z przewinięcia
                        // (0-1000), a zapisujemy najwyżej raz na 2 s, żeby nie mielić dysku.
                        onScrollFraction = { c, frac, off ->
                            val bookId = current!!.id
                            val now = System.currentTimeMillis()
                            val key = "$bookId|$c"
                            if (now - (scrollSaveAt[key] ?: 0L) > 2000L) {
                                scrollSaveAt[key] = now
                                val prevEntry = progress[bookId]
                                val entry = ReadingProgress(
                                    c, (frac * 1000f).toInt(), 1000, now, off,
                                    read = prevEntry?.read.orEmpty(),
                                )
                                progress = progress + (bookId to entry)
                                scope.launch(Dispatchers.IO) { ProgressStore.save(ctx, bookId, entry) }
                            }
                        },
                        initialAddr = progress[current!!.id]?.addr ?: 0,
                        initialScrollOffset = progress[current!!.id]?.scrollOffset ?: 0,
                        initialSeek = pendingSeek,
                    )
                }

                else -> Scaffold(
                    containerColor = p.uiBg,
                    bottomBar = {
                        BottomBar(tab, p) {
                            tab = it
                            chatFromReader = false
                            // zakładka Kaori to zawsze just talking, nie czat o książce
                            if (it == 1) chatThread = "free"
                        }
                    },
                ) { inner ->
                    // consumeWindowInsets mówi dzieciom, że te insety są już zużyte —
                    // bez tego imePadding w czacie doliczał jeszcze raz pasek nawigacji.
                    Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)) {
                        when (tab) {
                            0 -> LibraryScreen(
                                books = books,
                                progress = progress,
                                collections = collections,
                                onOpenBook = { meta ->
                                    scope.launch {
                                        val b = withContext(Dispatchers.IO) { LibraryStore.load(ctx, meta.id) }
                                        if (b == null) {
                                            error = "Can't load this book"
                                        } else {
                                            current = b
                                            chapter = (progress[meta.id]?.chapter ?: 0)
                                                .coerceIn(0, (b.chapters.size - 1).coerceAtLeast(0))
                                            seedRead(b)
                                            pendingSeek = null
                                            chatFromReader = false
                                            inReader = true
                                        }
                                    }
                                },
                                onAddBook = { picker.launch(arrayOf("*/*")) },
                                onOpenAt = { bookId, ch, off ->
                                    scope.launch {
                                        val b = withContext(Dispatchers.IO) { LibraryStore.load(ctx, bookId) }
                                        if (b == null) {
                                            error = "Can't load this book"
                                        } else {
                                            current = b
                                            chapter = ch.coerceIn(0, (b.chapters.size - 1).coerceAtLeast(0))
                                            seedRead(b)
                                            pendingSeek = Triple(ch, off, 0)
                                            chatFromReader = false
                                            inReader = true
                                        }
                                    }
                                },
                                onScanFolder = onScanFolder,
                                onPickFolder = { folderPicker.launch(null) },
                                onCollections = { updated ->
                                    collections = updated
                                    scope.launch(Dispatchers.IO) { CollectionsStore.save(ctx, updated) }
                                },
                                onDeleteBook = { meta ->
                                    books = books.filterNot { it.id == meta.id }
                                    collections = collections.map { c ->
                                        c.copy(bookIds = c.bookIds - meta.id)
                                    }
                                    progress = progress - meta.id
                                    scope.launch(Dispatchers.IO) {
                                        LibraryStore.delete(ctx, meta.id)
                                        ProgressStore.forget(ctx, meta.id)
                                        CollectionsStore.save(ctx, collections)
                                    }
                                },
                                onDeleteBooks = { doomed ->
                                    // Zbiorczo, bo w pętli `books` byłoby nieświeże
                                    // i zostawałyby duchy po wszystkich oprócz ostatniej.
                                    val ids = doomed.map { it.id }.toSet()
                                    books = books.filterNot { it.id in ids }
                                    collections = collections.map { c ->
                                        c.copy(bookIds = c.bookIds.filterNot { it in ids })
                                    }
                                    progress = progress.filterKeys { it !in ids }
                                    val snapshot = collections
                                    scope.launch(Dispatchers.IO) {
                                        ids.forEach { id ->
                                            LibraryStore.delete(ctx, id)
                                            ProgressStore.forget(ctx, id)
                                        }
                                        CollectionsStore.save(ctx, snapshot)
                                    }
                                },
                            )
                            1 -> ChatScreen(
                                threadId = chatThread,
                                bookTitle = if (chatThread.startsWith("book:")) current?.title else null,
                                apiKey = settings.apiKey,
                                visibleText = { if (chatThread.startsWith("book:")) visibleText() else "" },
                                visibleImages = {
                                    if (chatThread.startsWith("book:")) visibleImages() else emptyList()
                                },
                                contextInfo = if (chatThread.startsWith("book:")) spoilerInfo() else "",
                                config = llm,
                                persona = persona,
                                strictRole = settings.strictRole,
                                onOpenSettings = { tab = 3 },
                            )
                            2 -> StatsScreen()
                            else -> SettingsScreen(
                                palette = palette,
                                onPalette = { scope.launch { store.setTheme(it.id) } },
                                font = font,
                                onFont = { scope.launch { store.setFont(it.id) } },
                                fontSize = settings.fontSize,
                                onFontSize = { scope.launch { store.setFontSize(it) } },
                                scrollMode = settings.scrollMode,
                                onScrollMode = { scope.launch { store.setScrollMode(it) } },
                                onPinShortcut = {
                                    if (!pinAppShortcut(ctx)) {
                                        error = "This launcher does not allow pinning shortcuts"
                                    }
                                },
                                apiKey = settings.apiKey,
                                onApiKey = { scope.launch { store.setApiKey(it) } },
                                spoilerGuard = settings.spoilerGuard,
                                onSpoilerGuard = { scope.launch { store.setSpoilerGuard(it) } },
                                onExportBackup = { backupSave.launch("kaori-backup.json") },
                                onImportBackup = { backupOpen.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                personaName = persona.name,
                                onPersonas = { showPersonas = true },
                                memoryInfo = run {
                                    val (f, t, e) = app.kaori.reader.data.MemoryStore.counts(ctx, persona.id)
                                    "$f facts · $t threads · $e summaries"
                                },
                                onMemory = { showMemory = true },
                                strictRole = settings.strictRole,
                                onStrictRole = { scope.launch { store.setStrictRole(it) } },
                                baseUrl = settings.baseUrl,
                                model = settings.model,
                                onModelConfig = { u, m ->
                                    scope.launch {
                                        store.setBaseUrl(u)
                                        store.setModel(m)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            error?.let { msg ->
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.uiSurfaceRaised)
                        .clickable { error = null }
                        .padding(14.dp)
                ) {
                    Text(msg, color = p.text, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ImportingScreen(p: KaoriPalette, status: String) {
    Column(
        Modifier.fillMaxSize().background(p.uiBg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(status, color = p.text, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Text("EPUB parsing", color = p.textDim, fontSize = 11.sp)
    }
}

// --------------------------------------------------------------------------- //
//  Pasek dolny
// --------------------------------------------------------------------------- //
private val tabs = listOf(
    "▤" to "Library",
    "🎀" to "Kaori",
    "◔" to "Stats",
    "⚙" to "Settings",
)

@Composable
private fun BottomBar(selected: Int, p: KaoriPalette, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.uiSurface)
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { i, item ->
            val on = i == selected
            Column(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(item.first, fontSize = 15.sp, color = if (on) p.accent else p.textDim)
                Spacer(Modifier.height(3.dp))
                Text(
                    item.second,
                    fontSize = 9.sp,
                    color = if (on) p.accent else p.textDim,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// --------------------------------------------------------------------------- //
//  STATYSTYKI
// --------------------------------------------------------------------------- //
@Composable
private fun StatsScreen() {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var stats by remember { mutableStateOf<Map<String, DayStat>>(emptyMap()) }
    var streak by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            stats = StatsStore.load(ctx)
            streak = StatsStore.streak(ctx)
        }
    }

    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH)
    val today = stats[fmt.format(java.util.Date())] ?: DayStat()
    val week = (0..6).map { back ->
        val d = System.currentTimeMillis() - back * 86_400_000L
        val key = fmt.format(java.util.Date(d))
        java.text.SimpleDateFormat("EE", java.util.Locale.ENGLISH).format(java.util.Date(d)) to
            (stats[key] ?: DayStat())
    }.reversed()
    val weekPages = week.sumOf { it.second.pages }
    val weekMinutes = week.sumOf { it.second.minutes }
    val maxPages = (week.maxOfOrNull { it.second.pages } ?: 0).coerceAtLeast(1)
    val totalPages = stats.values.sumOf { it.pages }
    val totalMinutes = stats.values.sumOf { it.minutes }

    Column(Modifier.fillMaxSize().background(p.uiBg).padding(horizontal = 20.dp)) {
        Text(
            "Reading",
            color = p.text,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 26.dp, bottom = 20.dp),
        )

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(p.gradFrom.copy(alpha = 0.30f), p.gradTo.copy(alpha = 0.24f))
                    )
                )
                .padding(16.dp)
        ) {
            Column {
                Text("THIS WEEK", color = p.accent, fontSize = 10.sp, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (weekPages == 0) "—" else "$weekPages pages",
                    color = p.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (weekMinutes == 0) "start reading to see progress"
                    else "${weekMinutes} min · ${today.pages} pages today",
                    color = p.textMuted, fontSize = 11.sp,
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            week.forEach { (label, s) ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.55f)
                            .height((6 + 54 * s.pages / maxPages).dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (s.pages > 0) Brush.verticalGradient(listOf(p.gradFrom, p.gradTo))
                                else Brush.verticalGradient(listOf(p.uiSurfaceRaised, p.uiSurfaceRaised))
                            )
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(label, color = p.textDim, fontSize = 8.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("SERIES", color = p.textDim, fontSize = 10.sp, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(4.dp))
        StatRow(
            if (streak > 0) "$streak days in a row" else "No series",
            "read every day, even a page",
            if (streak > 0) "🔥" else "",
            p,
        )
        StatRow("Total", "$totalPages pages · $totalMinutes min", "", p)

        Spacer(Modifier.height(20.dp))
        Text("WITH KAORI", color = p.textDim, fontSize = 10.sp, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(4.dp))
        StatRow("Chat", "separate thread + one per book", "", p)
        StatRow("Memory", "local, on this phone only", "", p)
    }
}

@Composable
private fun StatRow(a: String, b: String, right: String, p: KaoriPalette) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(a, color = p.text, fontSize = 12.sp)
            Spacer(Modifier.height(3.dp))
            Text(b, color = p.textDim, fontSize = 10.sp)
        }
        if (right.isNotEmpty()) Text(right, color = p.accent, fontSize = 11.sp)
    }
}

