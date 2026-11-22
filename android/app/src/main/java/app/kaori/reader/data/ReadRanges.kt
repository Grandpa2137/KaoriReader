package app.kaori.reader.data

/**
 * Zakresy przeczytanego tekstu — w znakach CAŁEJ książki (rozdziały sklejone).
 *
 * Po co: Kaori ma znać tylko to, co użytkownik naprawdę przeczytał. Pojedynczy
 * „kursor = najdalej" psuje się przy skoku na koniec książki — wtedy znałaby
 * zakończenie. Dlatego trzymamy sumę przedziałów, a luki zostają nieprzeczytane.
 *
 * Zakresy są zawsze posortowane i scalone (bez dziur <= 1 znak), więc porównanie
 * dwóch list działa jak porównanie zbiorów.
 */
object ReadRanges {

    /** Powyżej tylu przedziałów scalamy najciaśniejsze dziury — inaczej plik rośnie bez końca. */
    private const val MAX_RANGES = 400

    /** Dodaje przeczytany kawałek [start, end) i zwraca nową, scaloną listę. */
    fun add(ranges: List<IntRange>, start: Int, end: Int): List<IntRange> {
        if (end <= start) return ranges
        val s = start
        val e = end - 1
        val out = ArrayList<IntRange>(ranges.size + 1)
        var ns = s
        var ne = e
        for (r in ranges) {
            // stykające się przedziały traktujemy jak jeden (r.last+1 == ns)
            if (r.last + 1 < ns || r.first > ne + 1) {
                out += r
            } else {
                if (r.first < ns) ns = r.first
                if (r.last > ne) ne = r.last
            }
        }
        out += ns..ne
        out.sortBy { it.first }
        return if (out.size <= MAX_RANGES) out else prune(out)
    }

    /** Łączy najciaśniejsze sąsiednie przedziały, aż lista zmieści się w limicie. */
    private fun prune(list: List<IntRange>): List<IntRange> {
        val work = ArrayList(list)
        while (work.size > MAX_RANGES) {
            var bestAt = 0
            var bestGap = Int.MAX_VALUE
            for (i in 0 until work.size - 1) {
                val gap = work[i + 1].first - work[i].last
                if (gap < bestGap) {
                    bestGap = gap
                    bestAt = i
                }
            }
            work[bestAt] = work[bestAt].first..work[bestAt + 1].last
            work.removeAt(bestAt + 1)
        }
        return work
    }

    /** Czy dany znak książki leży w przeczytanym zakresie. */
    fun isRead(ranges: List<IntRange>, offset: Int): Boolean =
        ranges.any { offset in it }

    /** Suma dwóch list zakresów — bez duplikatów i bez dziur na styku. */
    fun union(a: List<IntRange>, b: List<IntRange>): List<IntRange> {
        if (b.isEmpty()) return a
        if (a.isEmpty()) return b
        var out = a
        b.forEach { out = add(out, it.first, it.last + 1) }
        return out
    }

    /**
     * Tekst z przeczytanych zakresów, w kolejności książki.
     * Luki są pomijane — model nie dostaje tego, czego użytkownik nie widział.
     */
    fun textOf(book: Book, ranges: List<IntRange>): String {
        if (ranges.isEmpty()) return ""
        val sb = StringBuilder()
        var base = 0
        for (ch in book.chapters) {
            val len = ch.text.length
            if (len == 0) continue
            val cFirst = base
            val cLast = base + len - 1
            for (r in ranges) {
                if (r.first > cLast) break
                val s = maxOf(r.first, cFirst)
                val e = minOf(r.last, cLast)
                if (s > e) continue
                sb.append(ch.text, s - cFirst, e - cFirst + 1)
                sb.append('\n')
            }
            base += len
        }
        return sb.toString()
    }

    /** Offsety pierwszego znaku każdego rozdziału — do przeliczania pozycji. */
    fun chapterStarts(book: Book): IntArray {
        val out = IntArray(book.chapters.size + 1)
        var acc = 0
        book.chapters.forEachIndexed { i, ch ->
            out[i] = acc
            acc += ch.text.length
        }
        out[book.chapters.size] = acc
        return out
    }

    /**
     * Migracja: książki czytane przed wprowadzeniem zakresów dostają jeden
     * przedział „do dotychczasowej pozycji", żeby Kaori nie straciła nagle
     * tego, co już znają.
     */
    fun seedTo(book: Book, chapter: Int): List<IntRange> {
        if (book.chapters.isEmpty()) return emptyList()
        val starts = chapterStarts(book)
        val idx = chapter.coerceIn(0, book.chapters.lastIndex)
        val end = starts[idx] + book.chapters[idx].text.length
        return if (end <= 0) emptyList() else listOf(0 until end)
    }
}
