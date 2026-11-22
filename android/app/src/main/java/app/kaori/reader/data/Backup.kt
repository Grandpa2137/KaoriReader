package app.kaori.reader.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kopia zapasowa: postęp czytania, kolekcje, zakładki i statystyki.
 *
 * Tożsamością książki jest NAZWA PLIKU (`src`), nie wewnętrzne id — po imporcie
 * na innym telefonie id są inne, a nazwy te same. Wpisy bez pasującej książki
 * lądują w „poczekalni" i dopasowują się, gdy użytkownik doda tę książkę później.
 */
object Backup {

    private const val VERSION = 1
    private const val PENDING = "pending_backup.json"

    data class Report(
        val progress: Int = 0,
        val collections: Int = 0,
        val bookmarks: Int = 0,
        val pending: Int = 0,
    )

    // ------------------------------------------------------------------ eksport

    fun export(ctx: Context, uri: Uri): Report {
        val books = LibraryStore.list(ctx)
        val byId = books.associateBy { it.id }
        val progress = ProgressStore.load(ctx)
        val collections = CollectionsStore.load(ctx)
        val stats = StatsStore.load(ctx)

        val root = JSONObject()
            .put("app", "kaori-reader")
            .put("v", VERSION)
            .put("at", System.currentTimeMillis())

        val prog = JSONObject()
        progress.forEach { (bookId, p) ->
            val src = byId[bookId]?.src.orEmpty()
            if (src.isBlank()) return@forEach
            val o = JSONObject()
                .put("src", src)
                .put("c", p.chapter)
                .put("a", p.addr)
                .put("n", p.pages)
                .put("so", p.scrollOffset)
                .put("t", p.updated)
            val rr = JSONArray()
            p.read.forEach { rr.put(JSONArray().put(it.first).put(it.last)) }
            o.put("rr", rr)
            prog.put(src, o)
        }
        root.put("progress", prog)

        val cols = JSONArray()
        collections.forEach { c ->
            val arr = JSONArray()
            c.bookIds.mapNotNull { byId[it]?.src?.takeIf { s -> s.isNotBlank() } }.forEach { arr.put(it) }
            cols.put(JSONObject().put("name", c.name).put("books", arr))
        }
        root.put("collections", cols)

        val marks = JSONArray()
        books.forEach { meta ->
            BookmarksStore.forBook(ctx, meta.id).forEach { b ->
                if (meta.src.isBlank()) return@forEach
                marks.put(
                    JSONObject()
                        .put("src", meta.src)
                        .put("c", b.chapter)
                        .put("o", b.offset)
                        .put("sy", b.scrollY)
                        .put("t", b.title)
                        .put("s", b.snippet)
                )
            }
        }
        root.put("bookmarks", marks)

        val st = JSONObject()
        stats.forEach { (day, s) ->
            st.put(day, JSONObject().put("p", s.pages).put("m", s.minutes))
        }
        root.put("stats", st)

        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
            out.write(root.toString().toByteArray(Charsets.UTF_8))
        }

        return Report(
            progress = prog.length(),
            collections = cols.length(),
            bookmarks = marks.length(),
        )
    }

    // ------------------------------------------------------------------ import

    fun import(ctx: Context, uri: Uri): Report {
        val raw = ctx.contentResolver.openInputStream(uri)!!.use {
            it.readBytes().toString(Charsets.UTF_8)
        }
        val root = JSONObject(raw)
        if (root.optString("app") != "kaori-reader") {
            throw IllegalArgumentException("This is not a Kaori Reader backup")
        }

        val books = LibraryStore.list(ctx)
        val bySrc = books.filter { it.src.isNotBlank() }.associateBy { it.src }
        val byId = books.associateBy { it.id }
        var appliedProgress = 0
        var appliedCollections = 0
        var appliedBookmarks = 0
        val pending = JSONObject()

        // --- postęp ---
        val prog = root.optJSONObject("progress") ?: JSONObject()
        prog.keys().forEach { src ->
            val o = prog.getJSONObject(src)
            val meta = bySrc[src]
            if (meta == null) {
                pending.put(src, o)
                return@forEach
            }
            val old = ProgressStore.load(ctx)[meta.id]
            val incoming = ReadingProgress(
                chapter = o.optInt("c"),
                addr = o.optInt("a"),
                pages = o.optInt("n"),
                updated = o.optLong("t"),
                scrollOffset = o.optInt("so"),
                read = readRanges(o.optJSONArray("rr")),
            )
            val merged = if (old == null || incoming.updated > old.updated) {
                incoming.copy(read = ReadRanges.union(old?.read.orEmpty(), incoming.read))
            } else {
                old.copy(read = ReadRanges.union(old.read, incoming.read))
            }
            ProgressStore.save(ctx, meta.id, merged)
            appliedProgress++
        }

        // --- kolekcje: scalamy po nazwie ---
        val current = CollectionsStore.load(ctx).toMutableList()
        root.optJSONArray("collections")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                if (name.isEmpty()) continue
                val ids = ArrayList<String>()
                val srcs = o.optJSONArray("books") ?: JSONArray()
                for (j in 0 until srcs.length()) {
                    bySrc[srcs.optString(j)]?.let { ids += it.id }
                }
                val existing = current.indexOfFirst { it.name.equals(name, ignoreCase = true) }
                if (existing >= 0) {
                    val merged = (current[existing].bookIds + ids).distinct()
                    current[existing] = current[existing].copy(bookIds = merged)
                } else {
                    current += Collection(id = java.util.UUID.randomUUID().toString().take(8), name = name, bookIds = ids)
                }
                appliedCollections++
            }
        }
        CollectionsStore.save(ctx, current)

        // --- zakładki: bez duplikatów ---
        root.optJSONArray("bookmarks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val meta = bySrc[o.optString("src")] ?: continue
                val chapter = o.optInt("c")
                val offset = o.optInt("o", -1)
                val scrollY = o.optInt("sy", 0)
                val snippet = o.optString("s")
                val already = BookmarksStore.forBook(ctx, meta.id).any {
                    it.chapter == chapter && it.offset == offset &&
                        it.scrollY == scrollY && it.snippet == snippet
                }
                if (!already) {
                    BookmarksStore.add(
                        ctx,
                        Bookmark(
                            id = System.currentTimeMillis() + i,
                            bookId = meta.id,
                            chapter = chapter,
                            title = o.optString("t"),
                            snippet = snippet,
                            offset = offset,
                            scrollY = scrollY,
                        ),
                    )
                    appliedBookmarks++
                }
            }
        }

        // --- poczekalnia na książki, których jeszcze nie ma ---
        savePending(ctx, pending)
        byId.size // tylko żeby nie było ostrzeżenia o nieużywanym

        return Report(
            progress = appliedProgress,
            collections = appliedCollections,
            bookmarks = appliedBookmarks,
            pending = pending.length(),
        )
    }

    /**
     * Po dodaniu książki sprawdzamy poczekalnię — postęp z kopii wraca sam,
     * nawet jeśli książka trafiła na telefon później niż kopia.
     */
    fun applyPending(ctx: Context, meta: BookMeta) {
        if (meta.src.isBlank()) return
        val file = java.io.File(ctx.filesDir, PENDING)
        if (!file.exists()) return
        val all = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        val o = all.optJSONObject(meta.src) ?: return
        val old = ProgressStore.load(ctx)[meta.id]
        val incoming = ReadingProgress(
            chapter = o.optInt("c"),
            addr = o.optInt("a"),
            pages = o.optInt("n"),
            updated = o.optLong("t"),
            scrollOffset = o.optInt("so"),
            read = readRanges(o.optJSONArray("rr")),
        )
        val merged = if (old == null || incoming.updated > old.updated) {
            incoming.copy(read = ReadRanges.union(old?.read.orEmpty(), incoming.read))
        } else {
            old.copy(read = ReadRanges.union(old.read, incoming.read))
        }
        ProgressStore.save(ctx, meta.id, merged)
        all.remove(meta.src)
        runCatching { AtomicJson.write(file, all.toString()) }
    }

    private fun savePending(ctx: Context, pending: JSONObject) {
        val file = java.io.File(ctx.filesDir, PENDING)
        if (pending.length() == 0) {
            runCatching { file.delete() }
            return
        }
        val old = runCatching { JSONObject(file.readText()) }.getOrNull() ?: JSONObject()
        pending.keys().forEach { old.put(it, pending.get(it)) }
        runCatching { AtomicJson.write(file, old.toString()) }
    }

    private fun readRanges(arr: JSONArray?): List<IntRange> {
        if (arr == null) return emptyList()
        var out = emptyList<IntRange>()
        for (i in 0 until arr.length()) {
            val pair = arr.optJSONArray(i) ?: continue
            val s = pair.optInt(0, -1)
            val e = pair.optInt(1, -1)
            if (s >= 0 && e >= s) out = ReadRanges.add(out, s, e + 1)
        }
        return out
    }
}
