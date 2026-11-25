package app.kaori.reader.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Cache tłumaczeń na dysku.
 *
 * Po co: raz przetłumaczony rozdział nie powinien kosztować tokenów drugi raz.
 * Klucz to skrót tekstu źródłowego, nie numer strony — dzięki temu zmiana rozmiaru
 * czcionki (a więc i podziału na strony) nie psuje trafień, a `this page`
 * i `whole chapter` trzymają się osobno, bo mają inny tekst.
 *
 * Plik: filesDir/translations/<bookId>/<lang>_<hash>.txt
 */
object TranslationStore {

    private const val MAX_FILES = 400
    private const val MAX_BYTES = 30L * 1024 * 1024

    private fun dir(root: File, bookId: String) =
        File(root, "translations/" + bookId.replace(Regex("[^A-Za-z0-9_-]"), "_"))

    private fun root(ctx: Context): File = ctx.filesDir

    private fun key(text: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(20)
    }

    private fun file(root: File, bookId: String, lang: String, text: String) =
        File(dir(root, bookId), "${lang.lowercase()}_${key(text)}.txt")

    fun get(ctx: Context, bookId: String, lang: String, text: String): String? =
        getFrom(root(ctx), bookId, lang, text)

    /** Wariant testowalny: korzeń katalogu zamiast Context. */
    internal fun getFrom(root: File, bookId: String, lang: String, text: String): String? {
        val f = file(root, bookId, lang, text)
        // AtomicJson.read: przy uszkodzonym pliku sięga po .bak, zamiast udawać,
        // że tłumaczenia nigdy nie było (wtedy płacisz drugi raz za to samo).
        val body = AtomicJson.read(f) { it }?.takeIf { it.isNotBlank() } ?: return null
        // dotknięcie pliku, żeby porządkowanie wiedziało, co jest świeże
        runCatching { f.setLastModified(System.currentTimeMillis()) }
        return body
    }

    fun put(ctx: Context, bookId: String, lang: String, text: String, translation: String) =
        putInto(root(ctx), bookId, lang, text, translation)

    /** Wariant testowalny: korzeń katalogu zamiast Context. */
    internal fun putInto(root: File, bookId: String, lang: String, text: String, translation: String) {
        if (text.isBlank() || translation.isBlank()) return
        val d = dir(root, bookId)
        runCatching {
            d.mkdirs()
            AtomicJson.write(file(root, bookId, lang, text), translation)
        }
        prune(root)
    }

    /** Ile miejsca zajmują tłumaczenia — do pokazania w ustawieniach. */
    fun sizeBytes(ctx: Context): Long = sizeBytesIn(root(ctx))

    internal fun sizeBytesIn(base: File): Long {
        val dir = File(base, "translations")
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clear(ctx: Context) {
        val root = File(ctx.filesDir, "translations")
        runCatching { root.deleteRecursively() }
    }

    /** Tnie cache, gdy urośnie: najpierw po liczbie plików, potem po rozmiarze. */
    private fun prune(base: File) {
        val root = File(base, "translations")
        if (!root.exists()) return
        val files = root.walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toList()
        var total = files.sumOf { it.length() }
        var index = 0
        while (files.size - index > MAX_FILES) {
            total -= files[index].length()
            runCatching { files[index].delete() }
            index++
        }
        while (total > MAX_BYTES && index < files.size) {
            total -= files[index].length()
            runCatching { files[index].delete() }
            index++
        }
    }
}
