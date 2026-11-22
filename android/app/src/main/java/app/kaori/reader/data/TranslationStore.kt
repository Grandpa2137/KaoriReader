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

    private fun dir(ctx: Context, bookId: String) =
        File(File(ctx.filesDir, "translations"), bookId.replace(Regex("[^A-Za-z0-9_-]"), "_"))

    private fun key(text: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(20)
    }

    private fun file(ctx: Context, bookId: String, lang: String, text: String) =
        File(dir(ctx, bookId), "${lang.lowercase()}_${key(text)}.txt")

    fun get(ctx: Context, bookId: String, lang: String, text: String): String? {
        val f = file(ctx, bookId, lang, text)
        if (!f.exists()) return null
        val body = runCatching { f.readText() }.getOrNull() ?: return null
        if (body.isBlank()) return null
        // dotknięcie pliku, żeby porządkowanie wiedziało, co jest świeże
        runCatching { f.setLastModified(System.currentTimeMillis()) }
        return body
    }

    fun put(ctx: Context, bookId: String, lang: String, text: String, translation: String) {
        if (text.isBlank() || translation.isBlank()) return
        val d = dir(ctx, bookId)
        runCatching {
            d.mkdirs()
            AtomicJson.write(file(ctx, bookId, lang, text), translation)
        }
        prune(ctx)
    }

    /** Ile miejsca zajmują tłumaczenia — do pokazania w ustawieniach. */
    fun sizeBytes(ctx: Context): Long {
        val root = File(ctx.filesDir, "translations")
        if (!root.exists()) return 0L
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clear(ctx: Context) {
        val root = File(ctx.filesDir, "translations")
        runCatching { root.deleteRecursively() }
    }

    /** Tnie cache, gdy urośnie: najpierw po liczbie plików, potem po rozmiarze. */
    private fun prune(ctx: Context) {
        val root = File(ctx.filesDir, "translations")
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
