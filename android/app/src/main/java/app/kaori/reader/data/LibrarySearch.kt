package app.kaori.reader.data

import android.content.Context
import java.io.File

/**
 * Wyszukiwanie in text CAŁEJ biblioteki.
 *
 * Indeks to jeden plik na książkę: pierwsza linia = offsety rozdziałów,
 * dalej surowy tekst. Bez bazy, bez zależności — przy 150 książkach to ~50 MB
 * czytanych z dysku, a trafienie od razu wie, w którym rozdziale i znaku leży.
 */
object LibrarySearch {

    data class Hit(
        val bookId: String,
        val title: String,
        val chapter: Int,
        val offset: Int,
        val snippet: String,
    )

    private fun dir(ctx: Context) = File(ctx.filesDir, "search").apply { mkdirs() }
    private fun file(ctx: Context, bookId: String) = File(dir(ctx), "$bookId.txt")

    fun isIndexed(ctx: Context, bookId: String) = file(ctx, bookId).exists()

    /** Buduje indeks jednej książki. Zwraca true, jeśli powstał. */
    fun index(ctx: Context, book: Book): Boolean = runCatching {
        val starts = ReadRanges.chapterStarts(book)
        val header = starts.take(book.chapters.size).joinToString(",")
        val text = StringBuilder()
        book.chapters.forEach { text.append(it.text).append('\n') }
        // Nagłówek + tekst w jednym pliku: czytanie całości to jedno otwarcie.
        val f = file(ctx, book.id)
        AtomicJson.write(f, "$header\n$text")
        true
    }.getOrDefault(false)

    fun drop(ctx: Context, bookId: String) {
        runCatching { file(ctx, bookId).delete() }
    }

    /**
     * Przeszukuje indeksy. [onProgress] dostaje (zrobione, all) — wynik
     * pojawia się kawałkami, więc przy 150 książkach user widzi ruch, nie zamułkę.
     */
    fun find(
        ctx: Context,
        query: String,
        limit: Int = 120,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onPartial: (List<Hit>) -> Unit = {},
    ): List<Hit> {
        val needle = query.trim()
        if (needle.length < 3) return emptyList()
        val metas = LibraryStore.list(ctx)
        val hits = ArrayList<Hit>(limit)
        var done = 0
        for (meta in metas) {
            done++
            val f = file(ctx, meta.id)
            if (!f.exists()) {
                onProgress(done, metas.size)
                continue
            }
            runCatching {
                val raw = f.readText()
                val nl = raw.indexOf('\n')
                if (nl <= 0) return@runCatching
                val starts = raw.substring(0, nl).split(',').mapNotNull { it.toIntOrNull() }
                val text = raw.substring(nl + 1)
                var from = 0
                while (hits.size < limit) {
                    val at = text.indexOf(needle, from, ignoreCase = true)
                    if (at < 0) break
                    val chapter = chapterOf(starts, at)
                    if (chapter >= 0) {
                        hits += Hit(
                            bookId = meta.id,
                            title = meta.title,
                            chapter = chapter,
                            offset = at - starts[chapter],
                            snippet = snippetOf(text, at, needle.length),
                        )
                    }
                    from = at + needle.length
                }
            }
            onProgress(done, metas.size)
            if (hits.size >= limit) break
            if (done % 20 == 0) onPartial(hits.toList())
        }
        return hits
    }

    private fun chapterOf(starts: List<Int>, offset: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (starts[mid] <= offset) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }

    private fun snippetOf(text: String, at: Int, len: Int): String {
        val s = (at - 45).coerceAtLeast(0)
        val e = (at + len + 55).coerceAtMost(text.length)
        return text.substring(s, e).replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    }
}
