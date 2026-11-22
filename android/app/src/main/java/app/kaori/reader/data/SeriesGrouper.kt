package app.kaori.reader.data

/**
 * Automatyczne kolekcje z tytułów.
 *
 * Tytuły light novel są bałaganem: „Makeine V3”, „Makeine V10”,
 * „Too Many Losing Heroines! vol 2 Premium”. Wyciągamy z nich nazwę serii,
 * a numer tomu i cały marketingowy ogon wyrzucamy.
 */
object SeriesGrouper {

    private val brackets = Regex("""\[[^\]]*]|\([^)]*\)""")
    private val junk = Regex(
        """(?i)[\s\-_]*(fixed|polished|premium|clean|edited|unrated|ver\.?)\b.*$"""
    )
    private val volume = Regex("""(?i)[\s\-_]*\b(vol(?:ume)?\.?|tom|v)\s*0*(\d{1,3})\b.*$""")
    private val trailingNumber = Regex("""[\s\-_]*\b\d{1,3}$""")
    private val spaces = Regex("""[_\s]{2,}""")

    /** Nazwa serii wyciągnięta z tytułu pojedynczej książki. */
    fun seriesKey(title: String): String {
        var t = brackets.replace(title, " ")
        t = junk.replace(t, "")
        t = volume.replace(t, "")
        t = trailingNumber.replace(t, "")
        t = t.replace('_', ' ')
        t = spaces.replace(t, " ").trim()
        t = t.trimEnd('-', '–', '—', ',', ':', '!', '.', ' ')
        return if (t.length < 3) title.trim() else t
    }

    /** Serie, które mają co najmniej dwie książki. */
    fun group(books: List<BookMeta>): List<Pair<String, List<String>>> =
        books
            .groupBy { seriesKey(it.title).lowercase() }
            .filter { it.value.size >= 2 }
            .map { (_, list) ->
                val name = seriesKey(list.first().title)
                val ordered = list.sortedWith { a, b -> compareTitles(a.title, b.title) }
                name to ordered.map { it.id }
            }
            .sortedBy { it.first.lowercase() }

    /** Naturalne porównanie tytułów: „tom 2” przed „tom 10”. */
    fun compareTitles(a: String, b: String): Int {
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
}
