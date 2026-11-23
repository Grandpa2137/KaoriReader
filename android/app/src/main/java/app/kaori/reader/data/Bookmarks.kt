package app.kaori.reader.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Zakładka w książce: rozdział + miejsce w nim + skrawek tekstu na podglądzie. */
data class Bookmark(
    val id: Long,
    val bookId: String,
    val chapter: Int,
    val title: String,
    val snippet: String,
    /** Offset znaku w rozdziale; -1 gdy nieznany (tryb scroll bez pomiaru). */
    val offset: Int = -1,
    /** Offset pikselowy w rozdziale — ratuje powrót w trybie ciągłego scrolla. */
    val scrollY: Int = 0,
)

/**
 * Bookmarks trzymane w jednym pliku JSON. Mały zbiór, więc czytanie całości
 * przy każdym otwarciu dialogu jest tańsze niż utrzymywanie indeksu.
 */
object BookmarksStore {

    private fun file(context: Context) = File(context.filesDir, "bookmarks.json")

    private val lock = Any()

    fun load(context: Context): List<Bookmark> =
        // przy uszkodzonym pliku AtomicJson sięga po bookmarks.json.bak
        AtomicJson.read(file(context)) { text ->
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Bookmark(
                    id = o.optLong("id"),
                    bookId = o.optString("b"),
                    chapter = o.optInt("c"),
                    title = o.optString("t"),
                    snippet = o.optString("s"),
                    offset = o.optInt("o", -1),
                    scrollY = o.optInt("sy", 0),
                )
            }
        } ?: emptyList()

    fun forBook(context: Context, bookId: String): List<Bookmark> = synchronized(lock) {
        load(context)
            .filter { it.bookId == bookId }
            .sortedWith(compareBy({ it.chapter }, { it.offset }, { it.scrollY }))
    }

    fun add(context: Context, bm: Bookmark) = synchronized(lock) {
        save(context, load(context) + bm)
    }

    fun remove(context: Context, id: Long) = synchronized(lock) {
        save(context, load(context).filterNot { it.id == id })
    }

    private fun save(context: Context, list: List<Bookmark>) {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(
                JSONObject()
                    .put("id", b.id)
                    .put("b", b.bookId)
                    .put("c", b.chapter)
                    .put("t", b.title)
                    .put("s", b.snippet)
                    .put("o", b.offset)
                    .put("sy", b.scrollY)
            )
        }
        runCatching { AtomicJson.write(file(context), arr.toString()) }
    }
}
