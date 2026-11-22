package app.kaori.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kaori.reader.data.Book
import app.kaori.reader.data.BookMeta
import app.kaori.reader.data.Bookmark
import app.kaori.reader.data.BookmarksStore
import app.kaori.reader.data.ChapterImage
import app.kaori.reader.data.ReadRanges
import app.kaori.reader.ui.theme.AllFonts
import app.kaori.reader.ui.theme.AllPalettes
import app.kaori.reader.ui.theme.KaoriPalette
import app.kaori.reader.ui.theme.LocalPalette
import app.kaori.reader.ui.theme.ReaderFont
import app.kaori.reader.ui.theme.ReaderFontSizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --------------------------------------------------------------------------- //
//  BIBLIOTEKA
// --------------------------------------------------------------------------- //
// Library przeniesiona do LibraryScreen.kt (kolekcje, sortowanie, filtr „Started”)

// --------------------------------------------------------------------------- //
//  CZYTNIK
// --------------------------------------------------------------------------- //
/** Języki do tłumaczenia — lista, nie trzy chipy, bo ludzie czytają nie tylko po polsku. */
private val TRANSLATE_LANGS = listOf(
    "Polish", "English", "Japanese", "German", "French", "Spanish", "Italian", "Portuguese",
    "Russian", "Ukrainian", "Czech", "Dutch", "Swedish", "Turkish", "Chinese", "Korean",
    "Vietnamese", "Indonesian", "Thai",
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    book: Book,
    chapterIndex: Int,
    font: ReaderFont,
    fontSize: Float,
    scrollMode: Boolean,
    onScrollMode: (Boolean) -> Unit,
    onChapter: (Int) -> Unit,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onProgress: (chapter: Int, addr: Int, pages: Int) -> Unit,
    onScrollFraction: (chapter: Int, fraction: Float, scrollOffset: Int) -> Unit = { _, _, _ -> },
    onCharOffset: (Int) -> Unit = {},
    /** Przeczytany kawałek książki (offsety w znakach całej książki). */
    onSeen: (start: Int, end: Int) -> Unit = { _, _ -> },
    onReadingMinute: () -> Unit = {},
    initialAddr: Int = 0,
    initialScrollOffset: Int = 0,
    /** Skok wskazany z zewnątrz (wynik szukania w bibliotece). */
    initialSeek: Triple<Int, Int, Int>? = null,
    /** Tłumaczenie strony: potrzebny klucz i model, ten sam co w czacie. */
    apiKey: String = "",
    config: app.kaori.reader.data.LlmConfig = app.kaori.reader.data.LlmConfig(),
    /** „Przeczytane do tego miejsca" / „od tego miejsca nieprzeczytane" (offset w książce). */
    onMarkReadUpTo: (Int) -> Unit = {},
    onMarkUnreadFrom: (Int) -> Unit = {},
    /** Otwarcie przetłumaczonego rozdziału jako osobnego dokumentu (oryginał zostaje). */
    onReadTranslated: (lang: String, text: String, chapterIndex: Int) -> Unit = { _, _, _ -> },
) {
    val p = LocalPalette.current
    val chapter = book.chapters.getOrNull(chapterIndex)
    val measurer = rememberTextMeasurer()

    // Ekran nie może zgasnąć w trakcie czytania.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val window = (ctx as? android.app.Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val style = TextStyle(
        color = p.readerText,
        fontFamily = font.family,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.65f).sp,
    )

    // Rozmiar obszaru tekstu — decyduje o podziale na pages.
    var areaW by remember { mutableStateOf(0) }
    var areaH by remember { mutableStateOf(0) }
    // Progress w trybie scroll (procent rozdziału) — do stopki.
    var scrollPercent by remember { mutableStateOf(0) }

    // Pages całej książki. Przeliczane od nowa przy zmianie fontu, rozmiaru
    // albo obrotu ekranu, więc licznik zawsze pokazuje prawdę.
    var pagesByChapter by remember { mutableStateOf<List<List<Page>>?>(null) }
    var counted by remember { mutableStateOf(false) }
    var page by remember(chapterIndex) { mutableStateOf(0) }
    // Zapora przed zapętleniem auto-przejścia: bez niej powrót do poprzedniego
    // rozdziału natychmiast przeskakiwał do przodu (zapamiętany offset = dno).
    var advanceArmed by remember { mutableStateOf(true) }
    // Wejście w poprzedni rozdział ma otworzyć jego OSTATNIĄ stronę.
    var goToLastPage by remember { mutableStateOf(false) }
    // Okna czytnika: szukanie in text i zakładki.
    var showSearch by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    // Tłumaczenie: okno z wyborem języka i wynikiem obok oryginału.
    var showTranslate by remember { mutableStateOf(false) }
    var translateBusy by remember { mutableStateOf(false) }
    var translated by remember { mutableStateOf<String?>(null) }
    var translateErr by remember { mutableStateOf<String?>(null) }
    var translateTo by remember { mutableStateOf("Polish") }
    var translateFrom by remember { mutableStateOf("") }
    var translateCached by remember { mutableStateOf(false) }
    // Co tłumaczymy: widoczną stronę czy cały rozdział (ten drugi to kilka wywołań).
    var translateWholeChapter by remember { mutableStateOf(false) }
    var translateStep by remember { mutableStateOf(0) }
    var translateTotal by remember { mutableStateOf(0) }
    // Ręczne znaczniki: czytanie na dwóch urządzeniach albo powrót po roku.
    var showMarks by remember { mutableStateOf(false) }
    // Wybór języka docelowego — lista, nie trzy stałe chipy.
    var showLangPicker by remember { mutableStateOf(false) }
    // Reading na głos — systemowy syntezator, strona po stronie.
    val tts = remember { app.kaori.reader.data.TtsPlayer(ctx) }
    var speaking by remember { mutableStateOf(false) }
    var spokenKey by remember { mutableStateOf("") }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }
    // Skok do miejsca w książce: (rozdział, offset znaku lub -1, offset pikselowy).
    // Bez tego trafienie z wyszukiwania otwierało początek rozdziału, nie samą linię.
    var seek by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var seekTick by remember { mutableStateOf(0L) }
    // Zmierzone położenie kawałków tekstu w obrębie rozdziału — tylko dla celu skoku.
    val segTop = remember(book.id) { mutableStateMapOf<Int, Int>() }
    val segW = remember(book.id) { mutableStateMapOf<Int, Int>() }
    // Stan przewijania musi żyć tutaj: korzysta z niego i lista, i skok do trafienia.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = chapterIndex,
        initialFirstVisibleItemScrollOffset = initialScrollOffset,
    )
    val scope = rememberCoroutineScope()
    val paginationKey = "${book.id}|${font.id}|$fontSize|$areaW|$areaH|$scrollMode"

    LaunchedEffect(paginationKey) {
        // W trybie ciągłego scrolla pages nie są do niczego potrzebne.
        if (scrollMode) return@LaunchedEffect
        if (areaW <= 0 || areaH <= 0) return@LaunchedEffect
        PaginationCache.get(paginationKey)?.let {
            pagesByChapter = it
            counted = true
            return@LaunchedEffect
        }
        pagesByChapter = null
        counted = false
        val n = book.chapters.size
        val slots = arrayOfNulls<List<Page>>(n)
        fun snapshot(): List<List<Page>> = slots.map { it ?: emptyList() }
        // Najpierw rozdział, w którym jesteśmy — tekst pojawia się od razu,
        // reszta książki paginuje się w tle.
        val order = listOf(chapterIndex) + (0 until n).filter { it != chapterIndex }
        withContext(Dispatchers.Default) {
            for (i in order) {
                slots[i] = paginate(book.chapters[i].text, style, measurer, areaW, areaH)
                val snap = snapshot()
                withContext(Dispatchers.Main) { pagesByChapter = snap }
            }
        }
        counted = true
        PaginationCache.put(paginationKey, snapshot())
    }

    val allRaw = pagesByChapter
    // Ilustracja dostaje własną stronę tam, gdzie stała w rozdziale. Bez tego
    // EPUB gubił all grafiki (parser ich nawet nie zapisywał).
    val all = remember(allRaw, book.chapters) {
        allRaw?.mapIndexed { i, pages ->
            interleaveImages(pages, book.chapters.getOrNull(i)?.images.orEmpty())
        }
    }
    val chapterPages = all?.getOrNull(chapterIndex)
    val imagesByOffset = remember(chapterIndex, book.id, chapterPages?.size) {
        book.chapters.getOrNull(chapterIndex)?.images
            ?.associate { it.offset to it.path } ?: emptyMap()
    }
    val safePage = page.coerceIn(0, (chapterPages?.lastIndex ?: 0).coerceAtLeast(0))
    val pageOffset = remember(all, chapterIndex, chapterPages?.size) {
        all?.take(chapterIndex)?.sumOf { it.size } ?: 0
    }
    val totalPages = remember(all) { all?.sumOf { it.size } ?: 0 }

    // Offsety rozdziałów w „współrzędnych książki" — po nich zgłaszamy przeczytane kawałki.
    val chapterStarts = remember(book) { ReadRanges.chapterStarts(book) }

    // Zapis postępu — po przewróceniu pages i po policzeniu całości.
    LaunchedEffect(counted, chapterIndex, safePage, totalPages) {
        if (counted && totalPages > 0) onProgress(chapterIndex, pageOffset + safePage, totalPages)
    }

    // Pozycja znakowa w rozdziale — po niej Kaori wie, dokąd wolno jej czytać.
    LaunchedEffect(chapterPages, safePage) {
        onCharOffset(chapterPages?.getOrNull(safePage)?.start ?: 0)
    }

    // „Przeczytane" = strona, na której użytkownik naprawdę się zatrzymał.
    // Dwell 2 s odsiewa skoki ze spisu treści i wyszukiwania, więc Kaori nie
    // dostaje stron, które tylko przemknęły przez ekran.
    LaunchedEffect(scrollMode, chapterPages, safePage, chapterIndex) {
        if (scrollMode) return@LaunchedEffect
        val pg = chapterPages?.getOrNull(safePage) ?: return@LaunchedEffect
        if (pg.text == IMAGE_PAGE) return@LaunchedEffect
        kotlinx.coroutines.delay(2000)
        val base = chapterStarts.getOrElse(chapterIndex) { 0 }
        onSeen(base + pg.start, base + pg.start + pg.text.length)
    }

    // Minuta czytania — licznik do statystyk, dopóki czytnik jest otwarty.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            onReadingMinute()
        }
    }

    // Powrót do miejsca, w którym użytkownik skończył.
    var restored by remember(book.id) { mutableStateOf(false) }
    LaunchedEffect(counted, chapterPages) {
        if (counted && !restored && initialAddr > 0 && chapterPages != null) {
            val local = initialAddr - pageOffset
            if (local in 0..chapterPages.lastIndex) page = local
            restored = true
        }
    }

    // Cofnięcie rozdziału w trybie stron → otwórz jego ostatnią stronę.
    LaunchedEffect(chapterPages, goToLastPage) {
        if (goToLastPage && chapterPages != null && chapterPages.isNotEmpty()) {
            page = chapterPages.lastIndex
            goToLastPage = false
        }
    }

    // Skok wskazany z zewnątrz (wynik szukania w bibliotece) — ustawiamy raz,
    // przy pierwszym wejściu do czytnika.
    LaunchedEffect(book.id) {
        initialSeek?.let { seek = it }
    }

    // Reading na głos: mówi bieżącą stronę, po zakończeniu przewraca i mówi dalej.
    LaunchedEffect(speaking, safePage, chapterIndex, chapterPages, tts.ready) {
        if (!speaking) return@LaunchedEffect
        val pages = chapterPages ?: return@LaunchedEffect
        val pg = pages.getOrNull(safePage) ?: run {
            speaking = false
            return@LaunchedEffect
        }
        val key = "${book.id}|$chapterIndex|$safePage"
        if (key == spokenKey) return@LaunchedEffect
        spokenKey = key
        if (pg.text == IMAGE_PAGE) {
            kotlinx.coroutines.delay(600)
            if (safePage < pages.lastIndex) page = safePage + 1
            else if (chapterIndex < book.chapters.size - 1) onChapter(chapterIndex + 1)
            else speaking = false
            return@LaunchedEffect
        }
        tts.speak(pg.text) {
            if (safePage < pages.lastIndex) {
                page = safePage + 1
            } else if (chapterIndex < book.chapters.size - 1) {
                page = 0
                onChapter(chapterIndex + 1)
            } else {
                speaking = false
            }
        }
    }

    // Skok w trybie stron: najpierw rozdział, potem strona zawierająca trafiony znak.    // Klawiatura w oknie szukania zwęża okno i przelicza paginację, więc skok
    // powtarzamy przez chwilę — inaczej lądował o stronę dalej.
    LaunchedEffect(seek) {
        if (seek != null) {
            kotlinx.coroutines.delay(2500)
            seek = null
        }
    }
    LaunchedEffect(seek, scrollMode, chapterIndex, chapterPages) {
        val s = seek ?: return@LaunchedEffect
        if (scrollMode) return@LaunchedEffect
        if (s.first != chapterIndex) {
            onChapter(s.first)
            return@LaunchedEffect
        }
        if (s.second < 0) {
            seek = null
            return@LaunchedEffect
        }
        val pages = chapterPages ?: return@LaunchedEffect
        val idx = pages.indexOfLast { it.text != IMAGE_PAGE && it.start <= s.second }
        restored = true
        page = if (idx > 0) idx else 0
        if (System.currentTimeMillis() - seekTick > 2500) seek = null
    }

    Box(Modifier.fillMaxSize().background(p.readerBg)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "‹ ${book.title.take(18)}",
                    color = p.textDim,
                    fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable { onBack() }
                    .combinedClickable(
                        onClick = { onBack() },
                        onLongClick = { showMarks = true },
                    ),
            )
            Spacer(Modifier.weight(1f))
            // Reading na głos — tylko w trybie stron, bo tam „następna strona”
            // jest jednoznaczna (w scrollu trzeba by zgadywač, co jest ekranem).
            if (!scrollMode) {
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (speaking) {
                                tts.stop()
                                speaking = false
                            } else {
                                spokenKey = ""
                                speaking = true
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (speaking) "▮▮" else "▶",
                        fontSize = 11.sp,
                        color = if (speaking) p.accent else p.textDim,
                    )
                }
            }
            // Szukanie in text i zakładki — przy 600+ stronach bez tego nie da się czytać.
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { showSearch = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) { Text("🔍", fontSize = 13.sp) }
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { showBookmarks = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) { Text("⚑", fontSize = 13.sp, color = p.textDim) }
            // Tłumaczenie widocznego tekstu — bez wychodzenia z czytnika.
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                    showTranslate = true
                    translateErr = null
                    translateBusy = false
                }.padding(horizontal = 6.dp, vertical = 4.dp)
            ) { Text("文", fontSize = 13.sp, color = p.textDim) }
            Spacer(Modifier.width(6.dp))
            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(p.uiSurfaceRaised)) {
                ModeChip("Pages", !scrollMode, p) { onScrollMode(false) }
                ModeChip("Scroll", scrollMode, p) { onScrollMode(true) }
            }
        }

        // W trybie scroll tytuły rozdziałów są renderowane in text — przyklejony
        // u góry dublowałby pierwszy z nich.
        if (!scrollMode) {
            Text(
                chapter?.title ?: "—",
                color = p.textDim,
                fontSize = 9.sp,
                letterSpacing = 1.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(p.readerBg)
                    .padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }

        if (chapter == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Empty book", color = p.textDim, fontSize = 12.sp)
            }
        } else if (scrollMode) {
            // JEDEN ciągły LazyColumn po całej książce. Dzięki temu przewijanie w tył
            // przez granicę rozdziału działa samo — bez detektorów, zapór i auto-przejść.
            // listState żyje wyżej, bo korzysta z niego też skok do trafienia.

            // Skok do trafienia: przewiń do rozdziału, zmierz, w której linii jest znak,
            // i dociągnij dokładnie tam. Bez pomiaru lądowaliśmy na starcie rozdziału.
            LaunchedEffect(seek, scrollMode) {
                val s = seek ?: return@LaunchedEffect
                if (!scrollMode) return@LaunchedEffect
                segTop.clear(); segW.clear()
                val target = s.first.coerceIn(0, book.chapters.lastIndex)
                val ch = book.chapters[target]
                listState.scrollToItem(target, s.third.coerceAtLeast(0))
                if (s.second >= 0) {
                    val seg = scrollSegments(ch.text, ch.images)
                        .filterIsInstance<ScrollSeg.Text>()
                        .lastOrNull { it.start <= s.second }
                    // Czekamy, aż kawałek z trafieniem zostanie położony i poda szerokość.
                    var w = 0
                    for (attempt in 0 until 24) {
                        if (seg == null) break
                        w = segW[seg.start] ?: 0
                        if (w > 0) break
                        withFrameNanos { }
                    }
                    // Wysokość linii liczymy sami — Text nie woła onTextLayout,
                    // gdy układ jest cache'owany, więc nie da się na nim polegać.
                    if (seg != null && w > 0) {
                        val local = (s.second - seg.start).coerceIn(0, seg.text.length)
                        val r = measurer.measure(
                            AnnotatedString(seg.text),
                            style = style,
                            constraints = Constraints(maxWidth = w),
                        )
                        val line = r.getLineForOffset(local)
                        val y = (segTop[seg.start] ?: 0) + r.getLineTop(line).toInt()
                        listState.scrollToItem(target, (y - 80).coerceAtLeast(0))
                    }
                }
                seek = null
            }

            // Rozdział zmieniony „z zewnątrz” (stopka, powrót z czatu) → jedziemy do niego.
            LaunchedEffect(chapterIndex) {
                if (!listState.isScrollInProgress &&
                    listState.firstVisibleItemIndex != chapterIndex
                ) {
                    listState.scrollToItem(chapterIndex)
                }
            }

            // Raport pozycji: rozdział, procent książki, pikselowy offset w rozdziale.
            LaunchedEffect(Unit) {
                snapshotFlow {
                    Triple(
                        listState.firstVisibleItemIndex,
                        listState.firstVisibleItemScrollOffset,
                        listState.isScrollInProgress,
                    )
                }.collect { (idx, off, _) ->
                    val safe = idx.coerceIn(0, book.chapters.lastIndex)
                    if (safe != chapterIndex) onChapter(safe)
                    val allChars = book.chapters.sumOf { it.text.length }.coerceAtLeast(1)
                    val before = book.chapters.take(safe).sumOf { it.text.length }
                    onCharOffset(before)
                    val frac = (before.toFloat() / allChars).coerceIn(0f, 1f)
                    scrollPercent = (frac * 100f).toInt()
                    onScrollFraction(safe, frac, off)
                }
            }

            // „Przeczytane" w scrollu: łączymy to, co przejechało przez ekran płynnie.
            // Skok (spis treści, szukanie, zakładka) przerywa łącznik — ominięty
            // tekst zostaje dla Kaori nieprzeczytany.
            LaunchedEffect(book.id, scrollMode) {
                if (!scrollMode) return@LaunchedEffect
                var from = -1
                var last = -1
                snapshotFlow {
                    Triple(
                        listState.firstVisibleItemIndex,
                        listState.firstVisibleItemScrollOffset,
                        listState.isScrollInProgress,
                    )
                }.collect { (idx, off, scrolling) ->
                    val len = book.chapters.getOrNull(idx)?.text?.length ?: return@collect
                    val item = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == idx } ?: return@collect
                    val frac = if (item.size > 0) off.toFloat() / item.size else 0f
                    val top = chapterStarts.getOrElse(idx) { 0 } +
                        (len * frac.coerceIn(0f, 1f)).toInt()
                    if (from < 0) {
                        from = top
                        last = top
                        return@collect
                    }
                    // Skok in text = nowy początek łącznika.
                    if (kotlin.math.abs(top - last) > JUMP_SEEN_CHARS) from = top
                    if (!scrolling) {
                        onSeen(minOf(from, top), maxOf(from, top))
                        from = top
                    }
                    last = top
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
            ) {
                itemsIndexed(book.chapters) { i, ch ->
                    val segs = remember(i, ch.text, ch.images) {
                        scrollSegments(ch.text, ch.images)
                    }
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            ch.title,
                            color = p.textDim,
                            fontSize = 10.sp,
                            letterSpacing = 1.0.sp,
                            modifier = Modifier.padding(
                                top = if (i == 0) 6.dp else 28.dp,
                                bottom = 12.dp,
                            ),
                        )
                        // Kawałki tekstu mierzą swoje położenie, ale tylko wtedy, gdy
                        // są celem skoku — inaczej to zbędna praca przy każdym przewinięciu.
                        val targeting = seek?.first == i
                        segs.forEach { seg ->
                            when (seg) {
                                is ScrollSeg.Text -> Text(
                                    seg.text,
                                    style = style,
                                    modifier = if (targeting) {
                                        Modifier.onGloballyPositioned { c ->
                                            segTop[seg.start] = c.positionInParent().y.toInt()
                                            segW[seg.start] = c.size.width
                                        }
                                    } else Modifier,
                                )
                                is ScrollSeg.Image -> {
                                    val bmp = remember(seg.path) {
                                        android.graphics.BitmapFactory
                                            .decodeFile(seg.path)?.asImageBitmap()
                                    }
                                    if (bmp != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = bmp,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 520.dp)
                                                .padding(vertical = 10.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(48.dp)) }
            }
        } else {
            PageView(
                pages = chapterPages,
                page = safePage,
                imageOf = { p ->
                    if (p != null && p.text == IMAGE_PAGE) imagesByOffset[p.start] else null
                },
                onPage = { page = it },
                onPrev = {
                    // Lewa krawędź na pierwszej stronie → poprzedni rozdział, ostatnia strona.
                    if (chapterIndex > 0) {
                        advanceArmed = true
                        goToLastPage = true
                        onChapter(chapterIndex - 1)
                    }
                },
                onNext = {
                    // Prawa krawędź na ostatniej stronie → następny rozdział, pierwsza strona.
                    if (chapterIndex < book.chapters.size - 1) {
                        advanceArmed = true
                        goToLastPage = false
                        onChapter(chapterIndex + 1)
                    }
                },
                onSize = { w, h ->
                    areaW = w
                    // Klawiatura zwęża okno — nie pozwól jej w tle przeliczyć paginacji.
                    areaH = if (showSearch || showBookmarks) maxOf(areaH, h) else h
                },
                style = style,
                p = p,
                modifier = Modifier.weight(1f),
            )
        }

            Row(
                Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cele dotyku na całą wysokość paska — sam tekst 10sp był nie do trafienia.
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = chapterIndex > 0) {
                            onChapter(chapterIndex - 1)
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (chapterIndex > 0) {
                        Text("‹ prev", color = p.textDim, fontSize = 11.sp)
                    }
                }
                Text(
                    when {
                        scrollMode -> "ch. ${chapterIndex + 1}/${book.chapters.size} · $scrollPercent% of the book"
                        pagesByChapter == null -> "…"
                        counted -> "page ${pageOffset + safePage + 1} of $totalPages"
                        else -> "page ${pageOffset + safePage + 1}"
                    },
                    color = p.textDim,
                    fontSize = 10.sp,
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = chapterIndex < book.chapters.size - 1) {
                            onChapter(chapterIndex + 1)
                        },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (chapterIndex < book.chapters.size - 1) {
                        Text("next ›", color = p.textDim, fontSize = 11.sp)
                    }
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 56.dp)
                .size(50.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(p.gradFrom, p.gradTo)))
                .clickable { onOpenChat() },
            contentAlignment = Alignment.Center,
        ) { Text("🎀", fontSize = 20.sp) }

        // --- szukanie in text książki ---
        if (showSearch) {
            var q by remember { mutableStateOf("") }
            var hits by remember { mutableStateOf<List<Triple<Int, Int, String>>>(emptyList()) }
            var busy by remember { mutableStateOf(false) }
            LaunchedEffect(q) {
                val needle = q.trim()
                if (needle.length < 3) {
                    hits = emptyList(); busy = false; return@LaunchedEffect
                }
                busy = true
                // Nie liczymy trafień przy każdym znaku — dopiero gdy użytkownik przestanie pisać.
                kotlinx.coroutines.delay(180)
                hits = withContext(Dispatchers.Default) {
                    val out = mutableListOf<Triple<Int, Int, String>>()
                    for ((i, ch) in book.chapters.withIndex()) {
                        var from = 0
                        while (out.size < 60) {
                            val at = ch.text.indexOf(needle, from, ignoreCase = true)
                            if (at < 0) break
                            val s = (at - 40).coerceAtLeast(0)
                            val e = (at + needle.length + 40).coerceAtMost(ch.text.length)
                            out += Triple(i, at, ch.text.substring(s, e).replace('\n', ' ').trim())
                            from = at + needle.length
                        }
                        if (out.size >= 60) break
                    }
                    out
                }
                busy = false
            }
            AlertDialog(
                onDismissRequest = { showSearch = false },
                containerColor = p.uiBg,
                title = { Text("Search in book", fontSize = 15.sp) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = q,
                            onValueChange = { q = it },
                            singleLine = true,
                            label = { Text("at least 3 characters", fontSize = 11.sp) },
                        )
                        Spacer(Modifier.height(8.dp))
                        when {
                            busy -> Text("searching…", color = p.textDim, fontSize = 11.sp)
                            q.trim().length >= 3 && hits.isEmpty() ->
                                Text("no matches", color = p.textDim, fontSize = 11.sp)
                            else -> LazyColumn(Modifier.heightIn(max = 340.dp)) {
                                items(hits.size) { i ->
                                    val hit = hits[i]
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                showSearch = false
                                                seekTick = System.currentTimeMillis()
                                                seek = Triple(hit.first, hit.second, 0)
                                            }
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            book.chapters.getOrNull(hit.first)?.title ?: "—",
                                            color = p.accent,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            hit.third,
                                            color = p.text,
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSearch = false }) { Text("Close") }
                },
            )
        }

        // --- zakładki ---
        if (showBookmarks) {
            // Zapis i odczyt idą na IO — JSON przy każdym otwarciu okna nie ma prawa
            // blokować klatki czytnika.
            var list by remember { mutableStateOf(emptyList<Bookmark>()) }
            var rev by remember { mutableStateOf(0) }
            LaunchedEffect(rev) {
                list = withContext(Dispatchers.IO) { BookmarksStore.forBook(ctx, book.id) }
            }
            // Gdzie dokładnie jesteśmy — tego szuka zakładka, nie początku rozdziału.
            val hereChar = chapterPages?.getOrNull(safePage)?.start ?: 0
            val hereScroll = if (scrollMode && listState.firstVisibleItemIndex == chapterIndex) {
                listState.firstVisibleItemScrollOffset
            } else 0
            AlertDialog(
                onDismissRequest = { showBookmarks = false },
                containerColor = p.uiBg,
                title = { Text("Bookmarks", fontSize = 15.sp) },
                text = {
                    Column {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(p.accent.copy(alpha = 0.15f))
                                .clickable {
                                    val ch = chapter
                                    val body = ch?.text?.replace('\n', ' ')?.trim().orEmpty()
                                    val at = if (scrollMode) -1 else hereChar
                                    val snippet = if (at >= 0) {
                                        body.substring(at.coerceIn(0, body.length)).take(80)
                                    } else {
                                        body.take(80)
                                    }
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            BookmarksStore.add(
                                                ctx,
                                                Bookmark(
                                                    id = System.currentTimeMillis(),
                                                    bookId = book.id,
                                                    chapter = chapterIndex,
                                                    title = ch?.title ?: "—",
                                                    snippet = snippet,
                                                    offset = at,
                                                    scrollY = hereScroll,
                                                ),
                                            )
                                        }
                                        rev++
                                    }
                                }
                                .padding(10.dp)
                        ) { Text("+ bookmark this spot", color = p.accent, fontSize = 11.sp) }
                        Spacer(Modifier.height(8.dp))
                        if (list.isEmpty()) {
                            Text("no bookmarks in this book", color = p.textDim, fontSize = 11.sp)
                        }
                        LazyColumn(Modifier.heightIn(max = 340.dp)) {
                            items(list.size) { i ->
                                val bm = list[i]
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        Modifier.weight(1f).clickable {
                                            showBookmarks = false
                                            seekTick = System.currentTimeMillis()
                                            seek = Triple(bm.chapter, bm.offset, bm.scrollY)
                                        }
                                    ) {
                                        Text(
                                            bm.title,
                                            color = p.text,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            bm.snippet,
                                            color = p.textDim,
                                            fontSize = 10.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        "✕",
                                        color = p.textMuted,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .clickable {
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        BookmarksStore.remove(ctx, bm.id)
                                                    }
                                                    rev++
                                                }
                                            }
                                            .padding(8.dp),
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBookmarks = false }) { Text("Close") }
                },
            )
        }

        // --- znaczniki przeczytania ---
        if (showMarks) {
            val here = if (scrollMode) {
                // w scrollu bierzemy początek rozdziału — bezpieczniej mniej niż więcej
                chapterStarts.getOrElse(chapterIndex) { 0 }
            } else {
                val pg = chapterPages?.getOrNull(safePage)
                val base = chapterStarts.getOrElse(chapterIndex) { 0 }
                if (pg == null) base else base + pg.start + pg.text.length
            }
            AlertDialog(
                onDismissRequest = { showMarks = false },
                containerColor = p.uiBg,
                title = { Text("Reading marks", fontSize = 15.sp) },
                text = {
                    Column {
                        Text(
                            "Position about ${here} characters into the book. " +
                                "Useful when you read the same book somewhere else.",
                            color = p.textDim,
                            fontSize = 9.5.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(p.uiSurface)
                                .clickable { showMarks = false; onMarkReadUpTo(here) }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Mark read up to here", color = p.accent, fontSize = 11.5.sp) }
                        Spacer(Modifier.height(7.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(p.uiSurface)
                                .clickable { showMarks = false; onMarkUnreadFrom(here) }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Mark unread from here",
                                color = androidx.compose.ui.graphics.Color(0xFFE08A9A),
                                fontSize = 11.5.sp,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        // Uczciwie: znacznik zmienia to, co dostaje model, a nie to, co już wie.
                        Text(
                            "Marking unread stops the text from being sent from now on. " +
                                "What she already read stays in the chat history and in her memory " +
                                "until you clear it there.",
                            color = p.textDim,
                            fontSize = 9.sp,
                            lineHeight = 13.sp,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showMarks = false }) { Text("Close") }
                },
            )
        }

        // --- tłumaczenie: ustawienia przed wysłaniem ---
        if (showTranslate) {
            val pageText = chapterPages?.getOrNull(safePage)?.text.orEmpty()
            val source = when {
                translateWholeChapter -> chapter?.text.orEmpty()
                scrollMode -> chapter?.text.orEmpty()
                else -> pageText
            }
            val estTokens = app.kaori.reader.data.KaoriEngine.estimateTokens(source)
            val estOut = (estTokens / 2).coerceAtLeast(1)
            AlertDialog(
                onDismissRequest = { showTranslate = false },
                containerColor = p.uiBg,
                title = { Text("Translate", fontSize = 15.sp) },
                text = {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(false to "this page", true to "whole chapter").forEach { (whole, label) ->
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (translateWholeChapter == whole) p.accent.copy(alpha = 0.18f)
                                            else p.uiSurface
                                        )
                                        .clickable {
                                            translateWholeChapter = whole
                                            translateErr = null
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        label,
                                        color = if (translateWholeChapter == whole) p.accent else p.textMuted,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("INTO", color = p.textDim, fontSize = 8.5.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(5.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(p.uiSurface)
                                .clickable { showLangPicker = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(translateTo, color = p.text, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Text("change ▾", color = p.accent, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (source.isBlank()) "Nothing to translate here."
                            else "${source.length} characters · about $estTokens tokens in, " +
                                "up to $estOut out — charged by your own API provider.",
                            color = p.textDim,
                            fontSize = 9.5.sp,
                            lineHeight = 14.sp,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = source.isNotBlank(),
                        onClick = {
                            showTranslate = false
                            translateErr = null
                            translated = null
                            translateFrom = source
                            translateStep = 0
                            translateTotal = 0
                            val bookIdForCache = book.id
                            val lang = translateTo
                            scope.launch {
                                // Najpierw dysk: ten sam tekst w tym samym języku nie kosztuje drugi raz.
                                val cached = withContext(Dispatchers.IO) {
                                    app.kaori.reader.data.TranslationStore
                                        .get(ctx, bookIdForCache, lang, source)
                                }
                                if (cached != null) {
                                    translated = cached
                                    translateCached = true
                                    return@launch
                                }
                                translateCached = false
                                translateBusy = true
                                val res = app.kaori.reader.data.KaoriEngine.translate(
                                    apiKey = apiKey,
                                    config = config,
                                    text = source,
                                    target = lang,
                                    onProgress = { d, t ->
                                        translateStep = d
                                        translateTotal = t
                                    },
                                )
                                res.onSuccess { text ->
                                    translated = text
                                    withContext(Dispatchers.IO) {
                                        app.kaori.reader.data.TranslationStore
                                            .put(ctx, bookIdForCache, lang, source, text)
                                    }
                                }.onFailure { translateErr = it.message ?: "couldn't translate" }
                                translateBusy = false
                            }
                        }
                    ) {
                        Text(
                            when {
                                estTokens > 0 -> "Translate (≈$estTokens tok)"
                                else -> "Translate"
                            }
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTranslate = false }) { Text("Cancel") }
                },
            )
        }

        // --- wybór języka ---
        if (showLangPicker) {
            AlertDialog(
                onDismissRequest = { showLangPicker = false },
                containerColor = p.uiBg,
                title = { Text("Translate into", fontSize = 15.sp) },
                text = {
                    Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
                        TRANSLATE_LANGS.forEach { lang ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(
                                        if (translateTo == lang) p.accent.copy(alpha = 0.16f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        val changed = lang != translateTo
                                        translateTo = lang
                                        showLangPicker = false
                                        // Zmiana języka na ekranie wyniku = nowe tłumaczenie od razu.
                                        if (changed && translateFrom.isNotBlank()) {
                                            translated = null
                                            translateErr = null
                                            translateStep = 0
                                            translateTotal = 0
                                            val src = translateFrom
                                            val bookIdForCache = book.id
                                            scope.launch {
                                                val cached = withContext(Dispatchers.IO) {
                                                    app.kaori.reader.data.TranslationStore
                                                        .get(ctx, bookIdForCache, lang, src)
                                                }
                                                if (cached != null) {
                                                    translated = cached
                                                    translateCached = true
                                                    return@launch
                                                }
                                                translateCached = false
                                                translateBusy = true
                                                val res = app.kaori.reader.data.KaoriEngine.translate(
                                                    apiKey = apiKey,
                                                    config = config,
                                                    text = src,
                                                    target = lang,
                                                    onProgress = { d, t ->
                                                        translateStep = d
                                                        translateTotal = t
                                                    },
                                                )
                                                res.onSuccess { text ->
                                                    translated = text
                                                    withContext(Dispatchers.IO) {
                                                        app.kaori.reader.data.TranslationStore
                                                            .put(ctx, bookIdForCache, lang, src, text)
                                                    }
                                                }.onFailure { translateErr = it.message ?: "couldn't translate" }
                                                translateBusy = false
                                            }
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(lang, color = p.text, fontSize = 12.sp)
                                Spacer(Modifier.weight(1f))
                                if (translateTo == lang) Text("✓", color = p.accent, fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLangPicker = false }) { Text("Close") }
                },
            )
        }

        // --- wynik tłumaczenia: własny, pełny ekran (czytanie długiego rozdziału) ---
        if (translated != null || translateBusy || translateErr != null) {
            androidx.activity.compose.BackHandler {
                translated = null
                translateErr = null
                translateBusy = false
            }
            Box(Modifier.fillMaxSize().background(p.readerBg)) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "✕",
                            color = p.textMuted,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .clickable {
                                    translated = null
                                    translateErr = null
                                    translateBusy = false
                                }
                                .padding(6.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Translation", color = p.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                translateTo +
                                    (if (translateWholeChapter) " · whole chapter" else " · this page") +
                                    (if (translateCached) " · saved, 0 tokens" else ""),
                                color = if (translateCached) p.accent else p.textDim,
                                fontSize = 9.sp,
                            )
                        }
                        Text(
                            "Read",
                            color = p.accent,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(p.accent.copy(alpha = 0.20f))
                                .clickable(enabled = translated != null) {
                                    onReadTranslated(translateTo, translated.orEmpty(), chapterIndex)
                                    translated = null
                                    translateErr = null
                                    translateBusy = false
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "Copy",
                            color = p.textMuted,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(p.accent.copy(alpha = 0.14f))
                                .clickable {
                                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                    cm.setPrimaryClip(
                                        android.content.ClipData.newPlainText("translation", translated.orEmpty())
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "another ▾",
                            color = p.textMuted,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showLangPicker = true }
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                        )
                    }

                    when {
                        translateBusy -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (translateTotal > 1) "translating $translateStep / $translateTotal…"
                                else "translating…",
                                color = p.textDim,
                                fontSize = 12.sp,
                            )
                        }
                        translateErr != null -> Box(
                            Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                translateErr!!,
                                color = androidx.compose.ui.graphics.Color(0xFFE08A9A),
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        }
                        else -> Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 22.dp)
                                .padding(bottom = 28.dp),
                        ) {
                            Text(
                                translated!!,
                                color = p.readerText,
                                fontSize = (fontSize - 1f).coerceAtLeast(11f).sp,
                                lineHeight = ((fontSize - 1f).coerceAtLeast(11f) * 1.6f).sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, on: Boolean, p: KaoriPalette, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (on) p.accent.copy(alpha = 0.22f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(label, color = if (on) p.accent else p.textDim, fontSize = 9.sp)
    }
}

/** Koszt paginacji to kilkaset pomiarów tekstu — trzymamy wynik, żeby powrót do
 *  książki był natychmiastowy. Limit 3 książek, żeby nie zjadać RAM-u. */
private object PaginationCache {
    private val map = object : LinkedHashMap<String, List<List<Page>>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<List<Page>>>?) = size > 3
    }

    fun get(key: String): List<List<Page>>? = synchronized(map) { map[key] }

    fun put(key: String, value: List<List<Page>>) = synchronized(map) { map[key] = value }
}

/** Jedna strona czytnika: tekst i offset w rozdziale (potrzebny do blokady spoilerów). */
internal data class Page(val text: String, val start: Int)

/** Znacznik pages, na której jest sam obrazek — nie da się go pomylić z tekstem. */
private const val IMAGE_PAGE = "\u0001img"

/** Skok większy niż tyle znaków (spis treści, szukanie) nie liczy się jako przeczytany. */
private const val JUMP_SEEN_CHARS = 4000

/** Kawałek rozdziału w trybie scroll: tekst albo ilustracja. */
private sealed interface ScrollSeg {
    val start: Int

    data class Text(override val start: Int, val text: String) : ScrollSeg
    data class Image(override val start: Int, val path: String) : ScrollSeg
}

/** Dzieli rozdział na tekst i ilustracje w kolejności z książki. */
private fun scrollSegments(text: String, images: List<ChapterImage>): List<ScrollSeg> {
    if (images.isEmpty()) return listOf(ScrollSeg.Text(0, text))
    val out = ArrayList<ScrollSeg>(images.size * 2 + 1)
    var from = 0
    for (im in images.sortedBy { it.offset }) {
        val at = im.offset.coerceIn(0, text.length)
        if (at > from) out += ScrollSeg.Text(from, text.substring(from, at))
        out += ScrollSeg.Image(at, im.path)
        from = at
    }
    if (from < text.length) out += ScrollSeg.Text(from, text.substring(from))
    return out.ifEmpty { listOf(ScrollSeg.Text(0, text)) }
}

/**
 * Wstawia pages z ilustracjami w miejsca, w których stały w rozdziale.
 * Pusta lista obrazków zwraca pages bez zmian.
 */
private fun interleaveImages(pages: List<Page>, images: List<ChapterImage>): List<Page> {
    if (images.isEmpty()) return pages
    val out = ArrayList<Page>(pages.size + images.size)
    var next = 0
    for (p in pages) {
        while (next < images.size && images[next].offset <= p.start) {
            out += Page(IMAGE_PAGE, images[next].offset)
            next++
        }
        out += p
    }
    while (next < images.size) {
        out += Page(IMAGE_PAGE, images[next].offset)
        next++
    }
    return out
}

/** Paginacja: jeden układ tekstu na kawałek rozdziału, potem cięcie po liniach.
 *  Wcześniej mierzyliśmy każdą stronę osobno (tysiące pomiarów na książkę),
 *  przez co duże tomy otwierały się minutami. */
private fun paginate(text: String, style: TextStyle, measurer: TextMeasurer, w: Int, h: Int): List<Page> {
    if (text.isBlank()) return listOf(Page("", 0))
    val out = mutableListOf<Page>()
    var chunkStart = 0
    while (chunkStart < text.length) {
        var chunkEnd = minOf(text.length, chunkStart + 12000)
        if (chunkEnd < text.length) {
            // nie tniemy w środku zdania — dociągamy do końca akapitu
            val nl = text.indexOf('\n', chunkEnd)
            if (nl in 0..(chunkEnd + 4000)) chunkEnd = nl + 1
        }
        if (chunkEnd <= chunkStart) chunkEnd = minOf(text.length, chunkStart + 12000)
        val chunk = text.substring(chunkStart, chunkEnd)
        val layout = measurer.measure(
            AnnotatedString(chunk),
            style = style,
            constraints = Constraints(maxWidth = w),
        )
        var line = 0
        while (line < layout.lineCount) {
            val pageTop = layout.getLineTop(line)
            var last = line
            while (last + 1 < layout.lineCount &&
                layout.getLineBottom(last + 1) - pageTop <= h
            ) last++
            val s = layout.getLineStart(line)
            val e = maxOf(s, layout.getLineEnd(last, visibleEnd = false))
            val body = chunk.substring(s, e).trimEnd()
            out += Page(body, chunkStart + s)
            line = last + 1
        }
        chunkStart = chunkEnd
    }
    return out.ifEmpty { listOf(Page("", 0)) }
}

@Composable
private fun PageView(
    pages: List<Page>?,
    page: Int,
    imageOf: (Page?) -> String?,
    onPage: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSize: (Int, Int) -> Unit,
    style: TextStyle,
    p: KaoriPalette,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 22.dp)
                .onSizeChanged { onSize(it.width, it.height) }
        ) {
            val cur = pages?.getOrNull(page)
            val img = imageOf(cur)
            if (img != null) {
                val bmp = remember(img) {
                    android.graphics.BitmapFactory.decodeFile(img)?.asImageBitmap()
                }
                if (bmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("can't load the illustration", color = p.textDim, fontSize = 11.sp)
                    }
                }
            } else {
                Text(cur?.text ?: "…", style = style)
            }
        }

        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable {
                    if (page > 0) onPage(page - 1) else onPrev()
                }
            )
            Box(Modifier.weight(1.2f).fillMaxHeight())
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable {
                    if (pages != null && page < pages.lastIndex) onPage(page + 1) else onNext()
                }
            )
        }
    }
}

// --------------------------------------------------------------------------- //
//  USTAWIENIA
// --------------------------------------------------------------------------- //
@Composable
fun SettingsScreen(
    palette: KaoriPalette,
    onPalette: (KaoriPalette) -> Unit,
    font: ReaderFont,
    onFont: (ReaderFont) -> Unit,
    fontSize: Float,
    onFontSize: (Float) -> Unit,
    scrollMode: Boolean,
    onScrollMode: (Boolean) -> Unit,
    onPinShortcut: () -> Unit,
    apiKey: String,
    onApiKey: (String) -> Unit,
    spoilerGuard: Boolean,
    onSpoilerGuard: (Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    personaName: String,
    onPersonas: () -> Unit,
    memoryInfo: String,
    onMemory: () -> Unit,
    strictRole: Boolean,
    onStrictRole: (Boolean) -> Unit,
    baseUrl: String,
    model: String,
    onModelConfig: (String, String) -> Unit,
    translationInfo: String = "",
    onClearTranslations: () -> Unit = {},
) {
    val p = LocalPalette.current

    LazyColumn(
        Modifier.fillMaxSize().background(p.uiBg),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 24.dp),
    ) {
        item {
            Text("Settings", color = p.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(22.dp))

            Text("THEME", color = p.textDim, fontSize = 10.sp, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AllPalettes.forEach { pal ->
                    val on = pal.id == palette.id
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) pal.accent.copy(alpha = 0.18f) else p.uiSurface)
                            .clickable { onPalette(pal) }
                            .padding(10.dp),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(pal.gradFrom, pal.gradTo)))
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            pal.name,
                            color = if (on) pal.accent else p.textMuted,
                            fontSize = 11.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            Text("READER FONT", color = p.textDim, fontSize = 10.sp, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(4.dp))
            Text("tap to change — sample in this font", color = p.textDim, fontSize = 9.sp)
            Spacer(Modifier.height(10.dp))
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(AllFonts.size) { i ->
                    val f = AllFonts[i]
                    val on = f.id == font.id
                    Column(
                        Modifier
                            .width(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) p.accent.copy(alpha = 0.16f) else p.uiSurface)
                            .clickable { onFont(f) }
                            .padding(12.dp),
                    ) {
                        Text(
                            "Aa Bb 123",
                            color = if (on) p.accent else p.text,
                            fontFamily = f.family,
                            fontSize = 18.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(f.name, color = if (on) p.accent else p.textDim, fontSize = 10.sp)
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Text("TEXT SIZE", color = p.textDim, fontSize = 10.sp, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderFontSizes.forEach { s ->
                    val on = s == fontSize
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (on) p.accent.copy(alpha = 0.20f) else p.uiSurface)
                            .clickable { onFontSize(s) }
                            .padding(horizontal = 11.dp, vertical = 8.dp)
                    ) {
                        Text(
                            s.toInt().toString(),
                            color = if (on) p.accent else p.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("READING", color = p.textDim, fontSize = 10.sp, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Default mode", color = p.text, fontSize = 12.sp)
                    Text(if (scrollMode) "continuous scroll" else "pages", color = p.textDim, fontSize = 10.sp)
                }
                ModeChip("Pages", !scrollMode, p) { onScrollMode(false) }
                Spacer(Modifier.width(6.dp))
                ModeChip("Scroll", scrollMode, p) { onScrollMode(true) }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Spoiler guard", color = p.text, fontSize = 12.sp)
                    Text(
                        if (spoilerGuard) "She knows only what you have read"
                        else "She knows the whole book",
                        color = p.textDim,
                        fontSize = 10.sp,
                    )
                }
                ModeChip("On", spoilerGuard, p) { onSpoilerGuard(true) }
                Spacer(Modifier.width(6.dp))
                ModeChip("Off", !spoilerGuard, p) { onSpoilerGuard(false) }
            }

            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.accent.copy(alpha = 0.14f))
                    .clickable { onPinShortcut() }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Add a home screen shortcut",
                    color = p.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text("BACKUP", color = p.textDim, fontSize = 10.sp, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Progress, collections, bookmarks and stats. Books stay on the phone — " +
                    "the backup matches them by file name.",
                color = p.textDim,
                fontSize = 9.sp,
                lineHeight = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(p.uiSurface)
                        .clickable { onExportBackup() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Export", color = p.text, fontSize = 12.sp) }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(p.accent.copy(alpha = 0.16f))
                        .clickable { onImportBackup() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Import", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }

            // Tłumaczenia leżą na dysku — mówimy ile i pozwalamy je usunąć.
            if (translationInfo.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(p.uiSurface)
                        .clickable { onClearTranslations() }
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Saved translations", color = p.textDim, fontSize = 10.sp)
                        Text(translationInfo, color = p.text, fontSize = 12.sp)
                    }
                    Text("Clear", color = p.accent, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("PERSONA", color = p.textDim, fontSize = 10.sp, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.uiSurface)
                    .clickable { onPersonas() }
                    .padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Talking with", color = p.textDim, fontSize = 10.sp)
                    Text(personaName, color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Text("Change ›", color = p.accent, fontSize = 11.sp)
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.uiSurface)
                    .clickable { onMemory() }
                    .padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Memory", color = p.textDim, fontSize = 10.sp)
                    Text(memoryInfo, color = p.text, fontSize = 12.sp)
                }
                Text("Review ›", color = p.accent, fontSize = 11.sp)
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("In-character mode", color = p.text, fontSize = 12.sp)
                    Text(
                        if (strictRole) "every reply in character, zero AI mentions"
                        else "the model may break character",
                        color = p.textDim,
                        fontSize = 10.sp,
                    )
                }
                ModeChip("On", strictRole, p) { onStrictRole(true) }
                Spacer(Modifier.width(6.dp))
                ModeChip("Off", !strictRole, p) { onStrictRole(false) }
            }

            Spacer(Modifier.height(24.dp))
            Text("MODEL", color = p.textDim, fontSize = 10.sp, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(
                    Triple("DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
                    Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
                    Triple("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
                ).forEach { (label, url, m) ->
                    val on = baseUrl == url
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (on) p.accent.copy(alpha = 0.20f) else p.uiSurface)
                            .clickable { onModelConfig(url, m) }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(
                            label,
                            color = if (on) p.accent else p.textMuted,
                            fontSize = 9.5.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            var url by remember(baseUrl) { mutableStateOf(baseUrl) }
            var mdl by remember(model) { mutableStateOf(model) }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                label = { Text("API address", fontSize = 10.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = mdl,
                    onValueChange = { mdl = it },
                    singleLine = true,
                    label = { Text("model", fontSize = 10.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                ModeChip("Save", url != baseUrl || mdl != model, p) {
                    onModelConfig(url, mdl)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("API KEY", color = p.textDim, fontSize = 10.sp, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "The key stays on this phone only. Without it the chat cannot answer.",
                color = p.textDim,
                fontSize = 9.sp,
                lineHeight = 14.sp,
            )
            Spacer(Modifier.height(10.dp))
            var key by remember { mutableStateOf(apiKey) }
            // Klucz nie ma prawa wisieć na ekranie w całości — telefon bywa pokazywany.
            var reveal by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    singleLine = true,
                    placeholder = { Text("sk-…", fontSize = 11.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                    visualTransformation = if (reveal) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        Text(
                            if (reveal) "hide" else "show",
                            color = p.accent,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(6.dp).clickable { reveal = !reveal },
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                ModeChip("Save", key != apiKey, p) { onApiKey(key) }
            }
        }
    }
}
