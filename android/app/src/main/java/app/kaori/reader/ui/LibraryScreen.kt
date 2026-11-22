package app.kaori.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kaori.reader.data.BookMeta
import app.kaori.reader.data.Collection
import app.kaori.reader.data.LibrarySearch
import app.kaori.reader.data.LibraryStore
import app.kaori.reader.data.ReadingProgress
import app.kaori.reader.data.SeriesGrouper
import app.kaori.reader.ui.theme.KaoriPalette
import app.kaori.reader.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private enum class SortMode(val label: String) {
    Title("A–Z"),
    Recent("Recent"),
    Progress("Progress"),
    Size("Length"),
}

@Composable
fun LibraryScreen(
    books: List<BookMeta>,
    progress: Map<String, ReadingProgress>,
    collections: List<Collection>,
    onOpenBook: (BookMeta) -> Unit,
    onAddBook: () -> Unit,
    onScanFolder: () -> Unit,
    onPickFolder: () -> Unit = {},
    onCollections: (List<Collection>) -> Unit,
    onDeleteBook: (BookMeta) -> Unit,
    onDeleteBooks: (List<BookMeta>) -> Unit = {},
    onOpenAt: (bookId: String, chapter: Int, offset: Int) -> Unit = { _, _, _ -> },
) {
    val p = LocalPalette.current
    val ctx = androidx.compose.ui.platform.LocalContext.current

    var filter by remember { mutableStateOf("all") }
    var sort by remember { mutableStateOf(SortMode.Title) }
    var query by remember { mutableStateOf("") }
    var newCollectionDialog by remember { mutableStateOf(false) }
    var renameCollection by remember { mutableStateOf<Collection?>(null) }
    // Zaznaczanie wielu książek: po imporcie 15 tomów nie klika się ich po kolei.
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var bulkCollections by remember { mutableStateOf(false) }
    var bulkDelete by remember { mutableStateOf(false) }
    // Osobny ekran kolekcji — przy kilkudziesięciu półkach pasek chipów
    // przestaje być nawigacją, a zaczyna być przewijaniem w ciemno.
    var showCollections by remember { mutableStateOf(false) }

    // Szukanie w TREŚCI wszystkich książek — osobny tryb, bo wymaga indeksu.
    var inText by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<LibrarySearch.Hit>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var scanMsg by remember { mutableStateOf("") }

    LaunchedEffect(inText, query, books.size) {
        if (!inText || query.trim().length < 3) {
            hits = emptyList()
            scanning = false
            return@LaunchedEffect
        }
        scanning = true
        scanMsg = "preparing the index…"
        kotlinx.coroutines.delay(350)   // nie budź dysku przy każdym znaku
        val needle = query
        val found = withContext(Dispatchers.IO) {
            val metas = LibraryStore.list(ctx)
            metas.forEachIndexed { i, m ->
                if (!LibrarySearch.isIndexed(ctx, m.id)) {
                    withContext(Dispatchers.Main) { scanMsg = "indexing ${i + 1} / ${metas.size}" }
                    LibraryStore.load(ctx, m.id)?.let { b -> LibrarySearch.index(ctx, b) }
                }
            }
            withContext(Dispatchers.Main) { scanMsg = "searching…" }
            LibrarySearch.find(ctx, needle, onProgress = { d, all ->
                // postęp liczony co książkę; stan ustawiamy na głównym wątku
            })
        }
        hits = found
        scanning = false
        scanMsg = ""
    }

    val visible = remember(books, progress, collections, filter, sort, query) {
        val base = when {
            filter == "all" -> books
            filter == "started" -> books.filter { progress[it.id]?.started == true }
            else -> {
                val ids = collections.firstOrNull { it.id == filter }?.bookIds?.toSet() ?: emptySet()
                books.filter { it.id in ids }
            }
        }
        val searched = if (query.isBlank()) base else base.filter {
            it.title.contains(query.trim(), ignoreCase = true) ||
                it.author.contains(query.trim(), ignoreCase = true)
        }
        when (sort) {
            SortMode.Title -> searched.sortedWith { a, b -> SeriesGrouper.compareTitles(a.title, b.title) }
            SortMode.Recent -> searched.sortedByDescending { progress[it.id]?.updated ?: 0L }
            SortMode.Progress -> searched.sortedByDescending { progress[it.id]?.percent ?: 0f }
            SortMode.Size -> searched.sortedByDescending { it.chars }
        }
    }

    val continueBook = books
        .filter { progress[it.id]?.started == true }
        .maxByOrNull { progress[it.id]?.updated ?: 0L }

    // Na pasku chipów tylko największe półki — reszta pod „☰ all”.
    val quickCollections = remember(collections) {
        collections.sortedByDescending { it.bookIds.size }.take(6)
    }

    if (showCollections) {
        CollectionsScreen(
            books = books,
            progress = progress,
            collections = collections,
            onCollections = onCollections,
            onClose = { showCollections = false },
            onPick = { filter = it },
            onOpenBook = { showCollections = false; onOpenBook(it) },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(p.uiBg)) {
        if (selecting) {
            SelectionBar(
                count = picked.size,
                allSelected = visible.isNotEmpty() && picked.size == visible.size,
                p = p,
                onClose = { selecting = false; picked = emptySet() },
                onSelectAll = {
                    picked = if (picked.size == visible.size) emptySet()
                    else visible.map { it.id }.toSet()
                },
                onCollections = { bulkCollections = true },
                onDelete = { bulkDelete = true },
            )
        } else {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Library", color = p.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            // „Scan” wymagałby dostępu do wszystkich plików — publiczna wersja go nie ma.
            // + Add (SAF) robi to samo, bez żadnych uprawnień.
            PillButton("+ Add", p, filled = true) { onAddBook() }
        }

        // Szukanie po tytule i autorze
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(p.uiSurface)
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔍", fontSize = 12.sp, color = p.textDim)
            Spacer(Modifier.width(9.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("search title or author…", color = p.textDim, fontSize = 12.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = p.text, fontSize = 12.sp),
                    cursorBrush = Brush.verticalGradient(listOf(p.accent, p.accent)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Text(
                    "✕",
                    color = p.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { query = "" },
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (inText) p.accent.copy(alpha = 0.22f) else p.uiSurfaceRaised)
                    .clickable { inText = !inText }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                Text(
                    "in text",
                    color = if (inText) p.accent else p.textDim,
                    fontSize = 9.sp,
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Wejście do pełnej listy MUSI być widoczne bez przewijania — przy długich
            // nazwach kolekcji chip potrafił wylądować poza ekranem.
            item {
                Chip("☰ collections", showCollections, p) { showCollections = true }
            }
            item {
                Chip("All", filter == "all", p) { filter = "all" }
            }
            item {
                Chip("Started", filter == "started", p) { filter = "started" }
            }
            // Na pasku tylko największe półki — reszta pod „☰ collections”.
            items(quickCollections) { c ->
                Chip(
                    c.name.take(18) + if (c.name.length > 18) "…" else "",
                    filter == c.id,
                    p,
                    onLongClick = { renameCollection = c },
                ) { filter = c.id }
            }
            item {
                Chip("+ New", false, p) { newCollectionDialog = true }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("SORT", color = p.textDim, fontSize = 9.sp, letterSpacing = 1.2.sp)
            Spacer(Modifier.width(10.dp))
            SortMode.values().forEach { m ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (sort == m) p.accent.copy(alpha = 0.20f) else Color.Transparent)
                        .clickable { sort = m }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        m.label,
                        color = if (sort == m) p.accent else p.textDim,
                        fontSize = 9.sp,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            PillButton("Auto-collections", p, filled = false) {
                var updated = collections
                SeriesGrouper.group(books).forEach { (name, ids) ->
                    val existing = updated.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    updated = if (existing == null) {
                        updated + Collection(UUID.randomUUID().toString().take(8), name, ids)
                    } else {
                        updated.map {
                            if (it.id == existing.id) it.copy(bookIds = (it.bookIds + ids).distinct())
                            else it
                        }
                    }
                }
                onCollections(updated)
            }
        }
        }

        if (inText && query.trim().length >= 3) {
            Column(Modifier.fillMaxSize()) {
                if (scanning) {
                    Text(
                        scanMsg,
                        color = p.textDim,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                } else if (hits.isEmpty()) {
                    Text(
                        "no matches in the text",
                        color = p.textDim,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    items(hits.size) { i ->
                        val h = hits[i]
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpenAt(h.bookId, h.chapter, h.offset) }
                                .padding(horizontal = 6.dp, vertical = 9.dp)
                        ) {
                            Text(
                                h.title.take(44),
                                color = p.accent,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                h.snippet,
                                color = p.text,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            return@Column
        }

        if (books.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No books yet", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Put .epub files anywhere on the phone, then tap + Add and pick the file or folder.",
                    color = p.textDim,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            return@Column
        }

        if (visible.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Nothing found", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "try a shorter phrase",
                    color = p.textDim,
                    fontSize = 12.sp,
                )
            }
            return@Column
        }

        if (filter == "all" && query.isBlank() && !selecting && continueBook != null) {
            val pr = progress[continueBook.id]
            Box(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(p.gradFrom.copy(alpha = 0.26f), p.gradTo.copy(alpha = 0.20f))
                        )
                    )
                    .clickable { onOpenBook(continueBook) }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Cover(
                        continueBook.title,
                        Modifier.width(52.dp).height(74.dp),
                        coverPath = continueBook.cover,
                        showTitle = false,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "CONTINUE",
                            color = p.accent,
                            fontSize = 10.sp,
                            letterSpacing = 1.4.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            continueBook.title,
                            color = p.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "chapter ${(pr?.chapter ?: 0) + 1} · ${(pr?.percent ?: 0f).toInt()}% of the book",
                            color = p.textMuted,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(7.dp))
                        ProgressBar(pr?.percent ?: 0f, p)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        } else {
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(visible) { book ->
                val pr = progress[book.id]
                BookRow(
                    book = book,
                    percent = pr?.percent ?: 0f,
                    p = p,
                    selected = book.id in picked,
                    selecting = selecting,
                    onClick = {
                        if (selecting) {
                            picked = if (book.id in picked) picked - book.id else picked + book.id
                        } else onOpenBook(book)
                    },
                    onLongClick = {
                        if (selecting) {
                            picked = if (book.id in picked) picked - book.id else picked + book.id
                        } else {
                            selecting = true
                            picked = setOf(book.id)
                        }
                    },
                )
            }
            item { Spacer(Modifier.height(10.dp)) }
        }
    }

    // --- książka: przypisz to collection ---
    // Zastąpione trybem zaznaczania (long-press): te same akcje dla jednej
    // książki robi się przez zaznaczenie jednej i „to collection”.

    // --- nowa kolekcja / zmiana nazwy ---
    if (newCollectionDialog || renameCollection != null) {
        val editing = renameCollection
        var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
        AlertDialog(
            onDismissRequest = { newCollectionDialog = false; renameCollection = null },
            title = { Text(if (editing == null) "New collection" else "Rename", fontSize = 15.sp) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text("Name", fontSize = 11.sp) },
                    )
                    if (editing != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Delete collection",
                            color = Color(0xFFD9737A),
                            fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                onCollections(collections.filterNot { it.id == editing.id })
                                filter = "all"
                                renameCollection = null
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            val updated = if (editing == null) {
                                collections + Collection(
                                    UUID.randomUUID().toString().take(8),
                                    trimmed,
                                    emptyList(),
                                )
                            } else {
                                collections.map {
                                    if (it.id == editing.id) it.copy(name = trimmed) else it
                                }
                            }
                            onCollections(updated)
                        }
                        newCollectionDialog = false
                        renameCollection = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { newCollectionDialog = false; renameCollection = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // --- zbiorczo: dodaj zaznaczone to collection ---
    if (bulkCollections) {
        var bulkQuery by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { bulkCollections = false },
            title = { Text("Add ${picked.size} to collection", fontSize = 14.sp) },
            text = {
                Column {
                    SearchBox(bulkQuery, { bulkQuery = it }, "search collections…", p)
                    Spacer(Modifier.height(6.dp))
                    val shown = if (bulkQuery.isBlank()) collections
                    else collections.filter { it.name.contains(bulkQuery.trim(), ignoreCase = true) }
                    Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                        shown.forEach { c ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val updated = collections.map { col ->
                                            if (col.id != c.id) col
                                            else col.copy(bookIds = (col.bookIds + picked).distinct())
                                        }
                                        onCollections(updated)
                                        bulkCollections = false
                                        selecting = false
                                        picked = emptySet()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(c.name, color = p.text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Text("${c.bookIds.size}", color = p.textMuted, fontSize = 11.sp)
                            }
                        }
                        if (shown.isEmpty()) {
                            Text(
                                if (collections.isEmpty()) "No collections yet — add one under \"☰ collections\"."
                                else "no matches",
                                color = p.textDim, fontSize = 11.sp,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { bulkCollections = false }) { Text("Close") }
            },
        )
    }

    // --- zbiorczo: delete ---
    if (bulkDelete) {
        AlertDialog(
            onDismissRequest = { bulkDelete = false },
            title = { Text("Remove ${picked.size} from the library?", fontSize = 14.sp) },
            text = {
                Text(
                    "They will be removed from the phone together with their progress. Collections stay.",
                    color = p.textMuted, fontSize = 11.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val doomed = books.filter { it.id in picked }
                    onDeleteBooks(doomed)
                    bulkDelete = false
                    selecting = false
                    picked = emptySet()
                }) { Text("Delete", color = Color(0xFFD9737A)) }
            },
            dismissButton = {
                TextButton(onClick = { bulkDelete = false }) { Text("Cancel") }
            },
        )
    }
}

/** Pasek trybu zaznaczania — zastępuje nagłówek biblioteki. */
@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    p: KaoriPalette,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onCollections: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(10.dp)).clickable { onClose() }
                .padding(horizontal = 11.dp, vertical = 8.dp)
        ) { Text("✕", color = p.textDim, fontSize = 15.sp) }
        Text(
            "$count selected",
            color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.clip(RoundedCornerShape(9.dp)).clickable { onSelectAll() }
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Text(
                if (allSelected) "clear" else "all",
                color = p.textDim, fontSize = 10.sp,
            )
        }
        Spacer(Modifier.width(4.dp))
        PillButton("to collection", p, filled = true) { onCollections() }
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier.clip(RoundedCornerShape(9.dp)).clickable { onDelete() }
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) { Text("🗑", fontSize = 13.sp) }
    }
}

// --------------------------------------------------------------------------- //
//  KOLEKCJE — pełny ekran
// --------------------------------------------------------------------------- //
/**
 * Lista wszystkich półek: alfabetycznie, z liczbą książek, z jednym polem szukania,
 * które filtruje JEDNO I DRUGIE (kolekcje po nazwie, książki po tytule i autorze).
 * Przy stu kolekcjach pasek chipów w bibliotece nie wystarcza.
 */
@Composable
private fun CollectionsScreen(
    books: List<BookMeta>,
    progress: Map<String, ReadingProgress>,
    collections: List<Collection>,
    onCollections: (List<Collection>) -> Unit,
    onClose: () -> Unit,
    onPick: (String) -> Unit,
    onOpenBook: (BookMeta) -> Unit,
) {
    val p = LocalPalette.current

    var query by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Collection?>(null) }
    var creating by remember { mutableStateOf(false) }

    // Systemowy back ma wrócić do biblioteki, a nie wyrzucić z aplikacji.
    BackHandler { onClose() }

    val known = remember(books) { books.map { it.id }.toSet() }
    val counts = remember(collections, known) {
        collections.associate { c -> c.id to c.bookIds.count { it in known } }
    }
    val q = query.trim()

    val sorted = remember(collections, q) {
        val base = if (q.isBlank()) collections
        else collections.filter { it.name.contains(q, ignoreCase = true) }
        base.sortedWith { a, b -> compareNatural(a.name, b.name) }
    }
    val matchedBooks = remember(books, q) {
        if (q.length < 2) emptyList()
        else books
            .filter { it.title.contains(q, ignoreCase = true) || it.author.contains(q, ignoreCase = true) }
            .sortedWith { a, b -> SeriesGrouper.compareTitles(a.title, b.title) }
            .take(40)
    }

    Column(Modifier.fillMaxSize().background(p.uiBg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { onClose() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) { Text("‹", color = p.textDim, fontSize = 20.sp) }
            Text("Collections", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            PillButton("+ New", p, filled = true) { creating = true }
        }

        SearchBox(query, { query = it }, "search collections or books…", p)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (q.isBlank()) {
                item {
                    SpecialRow("All books", books.size, p) { onPick("all"); onClose() }
                }
                item {
                    SpecialRow(
                        "Started",
                        books.count { progress[it.id]?.started == true },
                        p,
                    ) { onPick("started"); onClose() }
                }
                item { SectionLabel("COLLECTIONS · ${collections.size}", p) }
            }

            if (sorted.isEmpty() && matchedBooks.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (collections.isEmpty()) "No collections yet" else "Nothing found",
                            color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (collections.isEmpty()) "\"+ New\" or \"Auto-collections\" in the library groups series for you."
                            else "try a shorter phrase",
                            color = p.textDim, fontSize = 11.sp, lineHeight = 16.sp,
                        )
                    }
                }
            }

            items(sorted, key = { "c" + it.id }) { c ->
                CollectionRow(
                    name = c.name,
                    count = counts[c.id] ?: 0,
                    p = p,
                    onClick = { onPick(c.id); onClose() },
                    onLongClick = { renaming = c },
                )
            }

            if (matchedBooks.isNotEmpty()) {
                item { SectionLabel("BOOKS · ${matchedBooks.size}", p) }
                items(matchedBooks, key = { "b" + it.id }) { b ->
                    BookRow(
                        book = b,
                        percent = progress[b.id]?.percent ?: 0f,
                        p = p,
                        onClick = { onOpenBook(b) },
                        onLongClick = { },
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (creating || renaming != null) {
        val editing = renaming
        var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
        AlertDialog(
            onDismissRequest = { creating = false; renaming = null },
            title = { Text(if (editing == null) "New collection" else "Rename", fontSize = 15.sp) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text("Name", fontSize = 11.sp) },
                    )
                    if (editing != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Delete collection (${editing.bookIds.size} books stay in the library)",
                            color = Color(0xFFD9737A),
                            fontSize = 11.sp,
                            modifier = Modifier.clickable {
                                onCollections(collections.filterNot { it.id == editing.id })
                                renaming = null
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            val updated = if (editing == null) {
                                collections + Collection(
                                    UUID.randomUUID().toString().take(8), trimmed, emptyList()
                                )
                            } else {
                                collections.map { if (it.id == editing.id) it.copy(name = trimmed) else it }
                            }
                            // Ta sama nazwa dwa razy = jedna półka, nie dwie identyczne.
                            onCollections(updated.distinctBy { it.name.lowercase() })
                        }
                        creating = false
                        renaming = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { creating = false; renaming = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SearchBox(
    query: String,
    onQuery: (String) -> Unit,
    hint: String,
    p: KaoriPalette,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.uiSurface)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔍", fontSize = 12.sp, color = p.textDim)
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(hint, color = p.textDim, fontSize = 12.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 12.sp),
                cursorBrush = Brush.verticalGradient(listOf(p.accent, p.accent)),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Text(
                "✕", color = p.textMuted, fontSize = 13.sp,
                modifier = Modifier.clickable { onQuery("") },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, p: KaoriPalette) {
    Text(
        text,
        color = p.textDim,
        fontSize = 9.sp,
        letterSpacing = 1.3.sp,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollectionRow(
    name: String,
    count: Int,
    p: KaoriPalette,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(p.uiSurface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("☰", color = p.textDim, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            color = p.text,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(count.toString(), color = p.textMuted, fontSize = 12.sp)
    }
}

@Composable
private fun SpecialRow(label: String, count: Int, p: KaoriPalette, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(p.uiSurface)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = p.accent, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(count.toString(), color = p.textMuted, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.bookItem(
    book: BookMeta,
    onClick: (BookMeta) -> Unit,
    onLong: () -> Unit,
): Modifier = this.combinedClickable(
    onClick = { onClick(book) },
    onLongClick = onLong,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(
    book: BookMeta,
    percent: Float,
    p: KaoriPalette,
    selected: Boolean = false,
    selecting: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) p.accent.copy(alpha = 0.16f) else p.uiSurface.copy(alpha = 0.55f)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Text(
                if (selected) "✓" else "○",
                color = if (selected) p.accent else p.textDim,
                fontSize = 15.sp,
            )
            Spacer(Modifier.width(9.dp))
        }
        Cover(book.title, Modifier.width(44.dp).height(63.dp), coverPath = book.cover, showTitle = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                book.title,
                color = p.text,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    if (book.author.isNotBlank()) append("${book.author} · ")
                    append("${book.chapterCount} ch.")
                    if (percent > 0f) append(" · ${percent.toInt()}%")
                },
                color = p.textDim,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (percent > 0f) {
                Spacer(Modifier.height(6.dp))
                ProgressBar(percent, p)
            }
        }
    }
}

@Composable
private fun ProgressBar(percent: Float, p: KaoriPalette) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(p.uiSurfaceRaised)
    ) {
        Box(
            Modifier
                .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                .height(3.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(p.gradFrom, p.gradTo)))
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    p: KaoriPalette,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) p.accent else p.uiSurface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = if (selected) p.uiBg else p.textMuted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 190.dp),
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PillButton(
    label: String,
    p: KaoriPalette,
    filled: Boolean,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) p.accent else p.accent.copy(alpha = 0.16f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = if (filled) p.uiBg else p.accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun Cover(
    title: String,
    modifier: Modifier,
    coverPath: String? = null,
    showTitle: Boolean = true,
) {
    // Dekodowanie okładki blokowało klatkę przy każdym wejściu w siatkę.
    var bmp by remember(coverPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(coverPath) {
        val path = coverPath?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        CoverCache.get(path)?.let { bmp = it; return@LaunchedEffect }
        val decoded = withContext(Dispatchers.IO) { decodeCover(path) }
        if (decoded != null) {
            CoverCache.put(path, decoded)
            bmp = decoded
        }
    }

    val hue = (title.hashCode() and 0xFF) / 255f * 360f
    val c1 = Color.hsv(hue, 0.42f, 0.55f)
    val c2 = Color.hsv((hue + 40f) % 360f, 0.35f, 0.30f)
    val cover = bmp

    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(c1, c2)))) {
        if (cover != null) {
            Image(
                bitmap = cover.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (showTitle) {
            Text(
                title,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 4,
                lineHeight = 12.sp,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(7.dp),
            )
        }
    }
}

/**
 * Okładki w pamięci: przy 155 książkach ponowne dekodowanie przy każdym
 * przewinięciu siatki było odczuwalne. Limit liczony z rozmiaru sterty.
 */
private object CoverCache {
    private val mem = object : android.util.LruCache<String, android.graphics.Bitmap>(
        (Runtime.getRuntime().maxMemory() / 12L / 1024L).toInt().coerceIn(2048, 16384),
    ) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.byteCount / 1024
    }

    fun get(key: String): android.graphics.Bitmap? = mem.get(key)

    fun put(key: String, bmp: android.graphics.Bitmap) = mem.put(key, bmp)
}

/** Okładka do 300 px szerokości — więcej nie widać w siatce, a RAM rośnie. */
private fun decodeCover(path: String): android.graphics.Bitmap? = runCatching {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 300) sample *= 2
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    android.graphics.BitmapFactory.decodeFile(path, opts)
}.getOrNull()

/**
 * Sortowanie naturalne: „tom 2” przed „tom 10”, a nie odwrotnie.
 * Bez tego cała biblioteka LN układa się alfabetycznie, ale numery tomów szaleją.
 */
internal fun compareNatural(a: String, b: String): Int {
    val x = a.lowercase()
    val y = b.lowercase()
    var i = 0
    var j = 0
    while (i < x.length && j < y.length) {
        if (x[i].isDigit() && y[j].isDigit()) {
            val si = i
            while (i < x.length && x[i].isDigit()) i++
            val sj = j
            while (j < y.length && y[j].isDigit()) j++
            val na = x.substring(si, i).trimStart('0').ifEmpty { "0" }
            val nb = y.substring(sj, j).trimStart('0').ifEmpty { "0" }
            if (na.length != nb.length) return na.length - nb.length
            val cmp = na.compareTo(nb)
            if (cmp != 0) return cmp
        } else {
            val cmp = x[i].compareTo(y[j])
            if (cmp != 0) return cmp
            i++
            j++
        }
    }
    return (x.length - i) - (y.length - j)
}
