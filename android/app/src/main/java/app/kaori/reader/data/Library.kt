package app.kaori.reader.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Kolekcja użytkownika — własna półka na książki. */data class Collection(
    val id: String,
    val name: String,
    val bookIds: List<String>,
)

/**
 * Progress czytania.
 *
 * Trzymamy globalny numer pages (`addr`) i łączną liczbę stron (`pages`),
 * żeby procent był liczony dla całej książki, a nie dla rozdziału.
 */
data class ReadingProgress(
    val chapter: Int = 0,
    val addr: Int = 0,
    val pages: Int = 0,
    val updated: Long = 0L,
    /** Offset w pikselach w trybie ciągłego scrolla — pozycja w rozdziale. */
    val scrollOffset: Int = 0,
    /** Przeczytane zakresy w znakach całej książki — po nich Kaori wie, do czego wolno jej czytać. */
    val read: List<IntRange> = emptyList(),
) {
    val percent: Float
        get() = if (pages <= 0) 0f else ((addr + 1).toFloat() / pages * 100f).coerceIn(0f, 100f)

    /** Czy książka jest zaczęta — czyli czy warto pokazać ją w filtrze „Started”. */
    val started: Boolean
        get() = updated > 0L
}

object ProgressStore {

    private fun file(context: Context) = File(context.filesDir, "progress.json")

    fun load(context: Context): Map<String, ReadingProgress> = loadFrom(file(context))

    /**
     * Wczytanie z konkretnego pliku — wydzielone, żeby dało się to przetestować na JVM
     * (uszkodzony plik, poprawny plik, brak pliku) bez Androida.
     */
    internal fun loadFrom(f: File): Map<String, ReadingProgress> {
        if (!f.exists()) return emptyMap()
        return runCatching {
            val obj = JSONObject(f.readText())
            obj.keys().asSequence().associateWith { key ->
                val o = obj.getJSONObject(key)
                ReadingProgress(
                    chapter = o.optInt("c"),
                    addr = o.optInt("a"),
                    pages = o.optInt("n"),
                    updated = o.optLong("t"),
                    scrollOffset = o.optInt("so"),
                    read = readFrom(o.optJSONArray("rr")),
                )
            }
        }.getOrElse {
            // plik nie parsuje się: odkładamy go na bok, żeby nie nadpisać danych
            AtomicJson.quarantine(f)
            emptyMap()
        }
    }

    /**
     * Ręczne ustawienie przeczytanych zakresów („mark unread from here").
     * Bez scalania — inaczej nie dałoby się cofnąć oznaczenia.
     */
    fun setRead(context: Context, bookId: String, ranges: List<IntRange>) {
        val all = load(context).toMutableMap()
        val old = all[bookId] ?: return
        all[bookId] = old.copy(read = ranges, updated = System.currentTimeMillis())
        writeAll(context, all)
    }

    private fun writeAll(context: Context, all: Map<String, ReadingProgress>) =
        writeTo(file(context), all)

    /** Zapis do konkretnego pliku — atomowy, używany też przez testy. */
    internal fun writeTo(target: java.io.File, all: Map<String, ReadingProgress>) {
        val obj = JSONObject()
        all.forEach { (k, v) ->
            obj.put(
                k,
                JSONObject().put("c", v.chapter).put("a", v.addr).put("n", v.pages)
                    .put("t", v.updated).put("so", v.scrollOffset)
                    .put("rr", readTo(v.read))
            )
        }
        runCatching { AtomicJson.write(target, obj.toString()) }
    }

    fun save(context: Context, bookId: String, p: ReadingProgress) {
        val all = load(context).toMutableMap()
        val old = all[bookId]
        // nie nadpisuj świeższego wpisu
        if (old != null && old.updated > p.updated) return
        // Przeczytane zakresy tylko rosną — zwykły zapis pozycji nie może ich skasować.
        val entry = if (old != null) p.copy(read = ReadRanges.union(old.read, p.read)) else p
        all[bookId] = entry
        val obj = JSONObject()
        all.forEach { (k, v) ->
            obj.put(
                k,
                JSONObject().put("c", v.chapter).put("a", v.addr).put("n", v.pages)
                    .put("t", v.updated).put("so", v.scrollOffset)
                    .put("rr", readTo(v.read))
            )
        }
        runCatching { AtomicJson.write(file(context), obj.toString()) }
    }

    fun forget(context: Context, bookId: String) {
        val all = load(context).toMutableMap()
        if (all.remove(bookId) == null) return
        val obj = JSONObject()
        all.forEach { (k, v) ->
            obj.put(
                k,
                JSONObject().put("c", v.chapter).put("a", v.addr).put("n", v.pages)
                    .put("t", v.updated).put("so", v.scrollOffset)
                    .put("rr", readTo(v.read))
            )
        }
        runCatching { AtomicJson.write(file(context), obj.toString()) }
    }

    private fun readTo(ranges: List<IntRange>): JSONArray {
        val arr = JSONArray()
        ranges.forEach { arr.put(JSONArray().put(it.first).put(it.last)) }
        return arr
    }

    private fun readFrom(arr: JSONArray?): List<IntRange> {
        if (arr == null) return emptyList()
        val out = ArrayList<IntRange>(arr.length())
        for (i in 0 until arr.length()) {
            val pair = arr.optJSONArray(i) ?: continue
            val s = pair.optInt(0, -1)
            val e = pair.optInt(1, -1)
            if (s >= 0 && e >= s) out += s..e
        }
        // plik mógł powstać w starszej wersji — normalizujemy przy odczycie
        var merged = emptyList<IntRange>()
        out.sortedBy { it.first }.forEach { merged = ReadRanges.add(merged, it.first, it.last + 1) }
        return merged
    }
}

object CollectionsStore {

    private fun file(context: Context) = File(context.filesDir, "collections.json")

    fun load(context: Context): List<Collection> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ids = o.optJSONArray("books") ?: JSONArray()
                Collection(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    bookIds = (0 until ids.length()).map { ids.getString(it) },
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, list: List<Collection>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("books", JSONArray(c.bookIds))
            )
        }
        runCatching { AtomicJson.write(file(context), arr.toString()) }
    }
}
