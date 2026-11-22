package app.kaori.reader.data

import android.content.Context
import android.net.Uri
import android.text.Html
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

/**
 * Własny parser EPUB.
 *
 * Dlaczego nie Readium: jego nawigator renderuje przez WebView, a tam władowanie
 * własnego fontu i normalna paginacja to obejścia. My renderujemy natywnie w
 * Compose, więc font z res/font działa od razu i mamy pełną kontrolę.
 */
/**
 * Ilustracja wewnątrz rozdziału.
 *
 * `offset` to pozycja w [Chapter.text], w której obrazek stał w oryginalnym HTML.
 * Czytnik wstawia go tam jako osobną stronę — inaczej pełnostronicowa grafika
 * rozjeżdża paginację tekstu.
 */
data class ChapterImage(
    val offset: Int,
    val path: String,
)

data class Chapter(
    val title: String,
    val text: String,
    val images: List<ChapterImage> = emptyList(),
)

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val chapters: List<Chapter>,
) {
    val totalChars: Int get() = chapters.sumOf { it.text.length }
}

/**
 * Lekki wpis biblioteki — bez rozdziałów.
 *
 * Trzymanie w pamięci trzynastu megabajtów tekstu przy 130 książkach kończy się
 * OOM-em, więc lista czyta tylko indeks, a pełną książkę wczytujemy przy otwarciu.
 */
data class BookMeta(
    val id: String,
    val title: String,
    val author: String,
    val chapterCount: Int,
    val chars: Int,
    val src: String = "",
    val cover: String = "",
) {
    val kiloTokens: Int get() = chars / 1000
}

object EpubParser {

    private const val MIN_CHAPTER_CHARS = 200

    // <img> zamieniamy na znacznik, bo Html.fromHtml zjada tagi razem z obrazkiem.
    // Dzięki temu po oczyszczeniu tekstu wiemy dokładnie, gdzie grafika stała.
    private const val IMG_MARK = "@@KIMG"
    // <img> oraz SVG-owe <image> (tak EPUB-y pakują pages tytułowe).
    private val IMG_TAG = Regex("(?is)<(?:img|image)\\b[^>]*>")
    private val IMG_SRC = Regex("(?is)(?:src|xlink:href)\\s*=\\s*[\"']([^\"']+)[\"']")
    private val IMG_MARK_RE = Regex("@@KIMG(\\d+)@@KIMG")

    /** Kopiuje plik z URI do prywatnego katalogu i zwraca File. */
    fun copyToStorage(context: Context, uri: Uri, id: String): File {
        val dir = File(context.filesDir, "epub").apply { mkdirs() }
        val out = File(dir, "$id.epub")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Can't open the file")
        return out
    }

    /** Parsuje EPUB do modelu Book. Jeśli podano `coverOut`, zapisuje tam okładkę. */
    fun parse(file: File, id: String, coverOut: File? = null, imgDir: File? = null): Book {
        ZipFile(file).use { zip ->
            // 1. container.xml -> ścieżka do OPF
            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: error("Not an EPUB (no META-INF/container.xml)")
            val opfPath = readRootfilePath(zip.getInputStream(containerEntry).readBytes())
                ?: error("No OPF file in the container")

            val opfDir = opfPath.substringBeforeLast('/', "")
            val opfBytes = zip.getEntry(opfPath)?.let { zip.getInputStream(it).readBytes() }
                ?: error("Brak pliku OPF: $opfPath")

            val (manifest, spineOrder) = readOpf(opfBytes)
            val title = readMeta(opfBytes, "title").ifBlank { file.nameWithoutExtension }
            val author = readMeta(opfBytes, "creator")
            val chapters = mutableListOf<Chapter>()

            // Pełnostronicowe ilustracje to w EPUB-ach OSOBNE pliki HTML: 500 znaków
            // i jeden <img>. Filtr treści je wyrzucał razem z grafiką — więc obrazki
            // z takich stron odkładamy i doklejamy do następnego rozdziału.
            var pendingImages = mutableListOf<ChapterImage>()
            var imgSeq = 0

            for (idref in spineOrder) {
                val href = manifest[idref] ?: continue
                val path = resolve(opfDir, href)
                val entry = zip.getEntry(path) ?: continue
                val html = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
                val srcs = mutableListOf<String>()
                val marked = IMG_TAG.replace(html) { m ->
                    val src = IMG_SRC.find(m.value)?.groupValues?.get(1)
                    if (src == null) "" else {
                        srcs += src
                        "$IMG_MARK${srcs.size - 1}$IMG_MARK"
                    }
                }
                val text = htmlToText(marked)
                val seq = imgSeq++

                if (!looksLikeContent(IMG_MARK_RE.replace(text, ""))) {
                    if (srcs.isNotEmpty()) {
                        val found = extractImages(text, srcs, path, zip, imgDir, seq).second
                        pendingImages += found.map { ChapterImage(0, it.path) }
                    }
                    continue
                }

                val spans = headingSpans(html)
                val chTitle = pickChapterTitle(html, spans, title) ?: "Chapter ${chapters.size + 1}"
                val body = stripLeadingHeadings(text, spans)
                val (clean, imgs) = extractImages(body, srcs, path, zip, imgDir, seq)
                chapters += Chapter(chTitle, clean, pendingImages + imgs)
                pendingImages = mutableListOf()
            }

            if (chapters.isEmpty()) {
                // awaryjnie: wszystko, co wygląda jak treść
                for (entry in zip.entries()) {
                    val name = entry.name.lowercase()
                    if (!name.endsWith(".xhtml") && !name.endsWith(".html")) continue
                    val html = zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)
                    val text = htmlToText(html)
                    if (looksLikeContent(text)) {
                        val sp = headingSpans(html)
                        chapters += Chapter(
                            pickChapterTitle(html, sp, title) ?: "Part ${chapters.size + 1}",
                            stripLeadingHeadings(text, sp),
                        )
                    }
                }
            }

            return Book(id = id, title = title, author = author, chapters = chapters).also {
                if (coverOut != null) runCatching { saveCover(zip, opfBytes, opfDir, coverOut) }
            }
        }
    }

    // -- okładka --------------------------------------------------------- //

    private data class OpfItem(val id: String, val href: String, val type: String, val props: String)

    /**
     * Wyciąga okładkę z EPUB-a. Kolejność prób: `properties="cover-image"`,
     * `<meta name="cover">`, guide `type="cover"`, plik z „cover” w nazwie, pierwszy obrazek.
     */
    private fun saveCover(zip: ZipFile, opfBytes: ByteArray, opfDir: String, out: File) {
        val items = mutableListOf<OpfItem>()
        var metaCoverId: String? = null
        var guideCoverHref: String? = null

        val p = Xml.newPullParser()
        p.setInput(opfBytes.inputStream(), "UTF-8")
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (p.name.substringAfter(':').lowercase()) {
                    "item" -> items += OpfItem(
                        id = p.getAttributeValue(null, "id") ?: "",
                        href = p.getAttributeValue(null, "href") ?: "",
                        type = p.getAttributeValue(null, "media-type") ?: "",
                        props = p.getAttributeValue(null, "properties") ?: "",
                    )
                    "meta" -> if (p.getAttributeValue(null, "name") == "cover") {
                        metaCoverId = p.getAttributeValue(null, "content")
                    }
                    "reference" -> if (p.getAttributeValue(null, "type") == "cover") {
                        guideCoverHref = p.getAttributeValue(null, "href")
                    }
                }
            }
            event = p.next()
        }

        val images = items.filter { it.type.startsWith("image/") }
        val byProps = images.firstOrNull { it.props.contains("cover-image") }
        val byMeta = images.firstOrNull { it.id == metaCoverId }
        val byGuess = images.firstOrNull { it.href.lowercase().substringAfterLast('/').contains("cover") }
        val candidate = byProps ?: byMeta ?: byGuess ?: images.firstOrNull()

        // guide wskazuje czasem stronę XHTML — wtedy wyciągamy z niej <img>
        var bytes: ByteArray? = candidate?.href?.let { href ->
            zip.getEntry(resolve(opfDir, href))?.let { zip.getInputStream(it).readBytes() }
        }
        if (bytes == null && guideCoverHref != null) {
            val pagePath = resolve(opfDir, guideCoverHref)
            val html = zip.getEntry(pagePath)?.let { zip.getInputStream(it).readBytes().toString(Charsets.UTF_8) }
            val img = html?.let { Regex("(?:src|xlink:href)=\"([^\"]+\\.(?:jpe?g|png))\"", RegexOption.IGNORE_CASE).find(it) }
            if (img != null) {
                val imgPath = resolve(pagePath.substringBeforeLast('/', ""), img.groupValues[1])
                bytes = zip.getEntry(imgPath)?.let { zip.getInputStream(it).readBytes() }
            }
        }
        if (bytes == null) return

        // zmniejszamy, żeby 155 okładek nie zjadło pamięci
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return
        var sample = 1
        while (bounds.outWidth / sample > 420) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return
        out.parentFile?.mkdirs()
        out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 86, it) }
        bmp.recycle()
    }

    // -- OPF ------------------------------------------------------------ //
    private fun readRootfilePath(bytes: ByteArray): String? {
        val p = Xml.newPullParser()
        p.setInput(bytes.inputStream(), "UTF-8")
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && p.name == "rootfile") {
                val path = p.getAttributeValue(null, "full-path")
                if (!path.isNullOrBlank()) return path
            }
            event = p.next()
        }
        return null
    }

    private fun readOpf(bytes: ByteArray): Pair<Map<String, String>, List<String>> {
        val manifest = mutableMapOf<String, String>()
        val spine = mutableListOf<String>()
        val p = Xml.newPullParser()
        p.setInput(bytes.inputStream(), "UTF-8")
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (p.name) {
                    "item" -> {
                        val id = p.getAttributeValue(null, "id")
                        val href = p.getAttributeValue(null, "href")
                        val type = p.getAttributeValue(null, "media-type") ?: ""
                        if (id != null && href != null && type.contains("html")) {
                            manifest[id] = href
                        }
                    }
                    "itemref" -> {
                        val idref = p.getAttributeValue(null, "idref")
                        if (idref != null) spine += idref
                    }
                }
            }
            event = p.next()
        }
        return manifest to spine
    }

    private fun readMeta(bytes: ByteArray, tag: String): String {
        val p = Xml.newPullParser()
        p.setInput(bytes.inputStream(), "UTF-8")
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                // XmlPullParser domyslnie obcina prefiksy (dc:title -> title),
                // wiec porownujemy po lokalnej nazwie
                val name = p.name.substringAfter(':').lowercase()
                if (name == tag.lowercase()) {
                    val text = p.nextText().trim()
                    if (text.isNotEmpty()) return text
                }
            }
            event = p.next()
        }
        return ""
    }

    private fun resolve(base: String, href: String): String {
        val clean = href.substringBefore('#')
        val raw = if (base.isBlank()) clean else "$base/$clean"
        // ZIP nie zna ścieżek względnych — "../Images/1.jpg" trzeba znormalizować.
        val parts = mutableListOf<String>()
        for (seg in raw.split('/')) {
            when (seg) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(seg)
            }
        }
        return parts.joinToString("/")
    }

    // -- obrazki -------------------------------------------------------- //
    /**
     * Wycina znaczniki z tekstu, zapisuje pliki graficzne z EPUB-a na dysk
     * i zwraca czysty tekst razem z pozycjami obrazków.
     */
    private fun extractImages(
        text: String,
        srcs: List<String>,
        pagePath: String,
        zip: ZipFile,
        outDir: File?,
        chapterIdx: Int,
    ): Pair<String, List<ChapterImage>> {
        if (outDir == null || srcs.isEmpty() || !IMG_MARK_RE.containsMatchIn(text)) {
            return IMG_MARK_RE.replace(text, "") to emptyList()
        }
        outDir.mkdirs()
        val baseDir = pagePath.substringBeforeLast('/', "")
        val parts = IMG_MARK_RE.split(text)
        val ids = IMG_MARK_RE.findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
        val sb = StringBuilder()
        val images = mutableListOf<ChapterImage>()

        parts.forEachIndexed { i, raw ->
            var s = raw
            if (i > 0) s = s.trimStart('\n', ' ')
            if (i < parts.size - 1) s = s.trimEnd('\n', ' ')
            sb.append(s)
            if (i < ids.size) {
                val offset = sb.length
                val src = srcs.getOrNull(ids[i])
                if (src != null) {
                    val entryPath = resolve(baseDir, src)
                    val entry = zip.getEntry(entryPath)
                    if (entry != null) {
                        val ext = entryPath.substringAfterLast('.', "jpg").lowercase()
                            .let { if (it in IMG_EXTS) it else "jpg" }
                        val out = File(outDir, "c${chapterIdx}_${images.size}.$ext")
                        val ok = runCatching {
                            zip.getInputStream(entry).use { input ->
                                out.outputStream().use { output -> input.copyTo(output) }
                            }
                        }.isSuccess
                        if (ok && out.length() > 0L) images += ChapterImage(offset, out.absolutePath)
                    }
                }
                if (i < parts.size - 1) sb.append("\n\n")
            }
        }
        return sb.toString().trim() to images
    }

    private val IMG_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    // -- HTML ------------------------------------------------------------ //
    private fun htmlToText(html: String): String {
        val body = Regex("<body[^>]*>(.*)</body>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: html
        val withBreaks = body.replace(Regex("<\\s*(br|/p|/div|/h[1-6]|/li)\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        val stripped = Html.fromHtml(withBreaks, Html.FROM_HTML_MODE_LEGACY).toString()
        return stripped
            .replace('\u00A0', ' ')
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !isJunk(it) }
            .joinToString("\n\n")
    }

    /** Same URL-e, sciezki do obrazkow itp. — nie sa trescia ksiazki. */
    private fun isJunk(line: String): Boolean {
        if (line.startsWith("http://") || line.startsWith("https://")) return true
        if (Regex("^[\\w./\\\\\\\\-]+\\.(png|jpe?g|gif|webp|svg)$", RegexOption.IGNORE_CASE).matches(line)) return true
        return false
    }

    /** Strona z trescia? Odrzuca okladki, stopki i pages z samymi linkami. */
    private fun looksLikeContent(text: String): Boolean {
        if (text.length < MIN_CHAPTER_CHARS) return false
        val letters = text.count { it.isLetter() }
        return letters.toFloat() / text.length >= 0.55f
    }

    /** All nagłówki w kolejności występowania: pozycja w HTML + tekst. */
    private fun headingSpans(html: String): List<Pair<Int, String>> =
        Regex("<h[1-6][^>]*>(.*?)</h[1-6]>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(html)
            .map { m ->
                m.range.first to Html.fromHtml(m.groupValues[1], Html.FROM_HTML_MODE_LEGACY).toString().trim()
            }
            .filter { it.second.isNotEmpty() && it.second.length <= 160 }
            .toList()

    /**
     * Tytuł rozdziału.
     *
     * W bloku tytułowym EPUB-y często dają dwa nagłówki: tytuł serii/tomu i nazwę rozdziału.
     * Tytuł książki z OPF-a bywa skrócony ("Villainess Fiancee"), więc dopasowywanie po nim
     * zawodzi. Bierzemy więc OSTATNI nagłówek przed pierwszym akapitem — to prawie zawsze
     * nazwa rozdziału.
     */
    private fun pickChapterTitle(html: String, spans: List<Pair<Int, String>>, bookTitle: String): String? {
        if (spans.isEmpty()) return null
        val firstP = Regex("<p[\\s>]", RegexOption.IGNORE_CASE).find(html)?.range?.first ?: Int.MAX_VALUE
        val beforeBody = spans.filter { it.first < firstP }.map { it.second }
        val pool = beforeBody.ifEmpty { spans.map { it.second } }
        val key = norm(bookTitle).take(40)
        val filtered = if (key.isEmpty()) pool else pool.filter { !norm(it).startsWith(key) }
        return (filtered.ifEmpty { pool }).last()
    }

    /** Tylko litery i cyfry, lowercase — do porównywania tytułów. */
    private fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Wiele EPUB-ów powtarza na początku treści nagłówki (tytuł serii, nazwa rozdziału).
     * Skoro tytuł rozdziału pokazujemy nad tekstem, usuwamy te akapity z treści.
     */
    private fun stripLeadingHeadings(text: String, heads: List<Pair<Int, String>>): String {
        val keys = heads.map { norm(it.second) }.filter { it.length >= 3 }.toSet()
        if (keys.isEmpty()) return text
        val paras = text.split("\n\n").toMutableList()
        var guard = 0
        while (paras.size > 1 && guard++ < 5) {
            val head = norm(paras.first())
            if (head.isNotEmpty() && head in keys) paras.removeAt(0) else break
        }
        return paras.joinToString("\n\n")
    }
}

// --------------------------------------------------------------------------- //
//  Library — prosty magazyn na pliki JSON
// --------------------------------------------------------------------------- //
object LibraryStore {

    private fun dir(context: Context) = File(context.filesDir, "books").apply { mkdirs() }
    private fun indexPath(context: Context) = File(dir(context), "index.json")

    /** Plik okładki danej książki. */
    fun coverFile(context: Context, id: String): File =
        File(File(context.filesDir, "covers").apply { mkdirs() }, "$id.jpg")

    /** Katalog na ilustracje z wnętrza książki. */
    fun imageDir(context: Context, id: String): File =
        File(File(context.filesDir, "bookimg").apply { mkdirs() }, id).apply { mkdirs() }

    /** Lista książek z indeksu — szybka, bez parsowania treści. */
    fun list(context: Context): List<BookMeta> {
        val idx = indexPath(context)
        if (idx.exists()) {
            val out = runCatching {
                val arr = JSONArray(idx.readText())
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    BookMeta(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        author = o.optString("author", ""),
                        chapterCount = o.optInt("chapters", 0),
                        chars = o.optInt("chars", 0),
                        src = o.optString("src", ""),
                        cover = o.optString("cover", ""),
                    )
                }
            }.getOrNull()
            if (out != null) {
                // pliki usunięte poza aplikacją? dosynchronizuj indeks
                val exist = dir(context).listFiles { f -> f.name.endsWith(".json") && f.name != "index.json" }
                    ?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()
                return out.filter { it.id in exist }.sortedBy { it.title.lowercase() }
            }
        }
        // brak indeksu (stara instalacja) — odbuduj z plików
        val metas = (dir(context).listFiles { f -> f.name.endsWith(".json") && f.name != "index.json" } ?: emptyArray())
            .mapNotNull { runCatching { read(it).toMeta() }.getOrNull() }
            .sortedBy { it.title.lowercase() }
        writeIndex(context, metas)
        return metas
    }

    /**
     * Usuwa duplikaty: ten sam plik źródłowy wczytany więcej niż raz.
     * Zostawia pierwszy wpis, resztę kasuje razem z okładkami.
     */
    fun dedupe(context: Context): Int {
        val metas = list(context)
        val seen = HashSet<String>()
        val keep = mutableListOf<BookMeta>()
        val drop = mutableListOf<BookMeta>()
        metas.forEach { m ->
            val key = m.src.ifBlank { m.id }
            if (seen.add(key)) keep += m else drop += m
        }
        if (drop.isEmpty()) return 0
        drop.forEach { m ->
            File(dir(context), "${m.id}.json").delete()
            coverFile(context, m.id).delete()
        }
        writeIndex(context, keep)
        return drop.size
    }

    /** Pełna książka — tylko dla otwieranej pozycji. */
    fun load(context: Context, id: String): Book? {
        val f = File(dir(context), "$id.json")
        if (!f.exists()) return null
        return runCatching { read(f) }.getOrNull()
    }

    fun save(context: Context, book: Book, src: String = "") {
        val cover = coverFile(context, book.id)
        val hasCover = cover.exists()
        val obj = JSONObject().apply {
            put("id", book.id)
            put("title", book.title)
            put("author", book.author)
            put("src", src)
            if (hasCover) put("cover", cover.absolutePath)
            val arr = JSONArray()
            book.chapters.forEach { c ->
                val co = JSONObject().put("t", c.title).put("x", c.text)
                if (c.images.isNotEmpty()) {
                    val ia = JSONArray()
                    c.images.forEach { im ->
                        ia.put(JSONObject().put("o", im.offset).put("p", im.path))
                    }
                    co.put("i", ia)
                }
                arr.put(co)
            }
            put("chapters", arr)
        }
        File(dir(context), "${book.id}.json").writeText(obj.toString())

        val metas = list(context).filterNot { it.id == book.id } + book.toMeta(src, if (hasCover) cover.absolutePath else "")
        writeIndex(context, metas.sortedBy { it.title.lowercase() })
    }

    fun delete(context: Context, id: String) {
        File(dir(context), "$id.json").delete()
        File(File(context.filesDir, "epub"), "$id.epub").delete()
        coverFile(context, id).delete()
        imageDir(context, id).deleteRecursively()
        writeIndex(context, list(context).filterNot { it.id == id })
    }

    private fun writeIndex(context: Context, metas: List<BookMeta>) {
        val arr = JSONArray()
        metas.forEach { m ->
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("title", m.title)
                    .put("author", m.author)
                    .put("chapters", m.chapterCount)
                    .put("chars", m.chars)
                    .put("src", m.src)
                    .put("cover", m.cover)
            )
        }
        runCatching { indexPath(context).writeText(arr.toString()) }
    }

    private fun Book.toMeta(src: String = "", cover: String = "") =
        BookMeta(id, title, author, chapters.size, totalChars, src, cover)

    private fun read(file: File): Book {
        val obj = JSONObject(file.readText())
        val arr = obj.getJSONArray("chapters")
        val chapters = ArrayList<Chapter>(arr.length())
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val imgs = c.optJSONArray("i")?.let { ia ->
                (0 until ia.length()).mapNotNull { k ->
                    val o = ia.optJSONObject(k) ?: return@mapNotNull null
                    val p = o.optString("p", "")
                    if (p.isEmpty()) null else ChapterImage(o.optInt("o", 0), p)
                }
            }.orEmpty()
            chapters += Chapter(c.getString("t"), c.getString("x"), imgs)
        }
        return Book(
            id = obj.getString("id"),
            title = obj.getString("title"),
            author = obj.optString("author", ""),
            chapters = chapters,
        )
    }
}
