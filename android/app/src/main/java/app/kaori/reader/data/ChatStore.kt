package app.kaori.reader.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Historia czatu per wątek: "free" (just talking) albo "book:<id>" (rozmowa o książce).
 * Bez tego każde przełączenie zakładki kasowało rozmowę.
 */
object ChatStore {

    private const val KEEP = 250

    private fun dir(context: Context) = File(context.filesDir, "chats").apply { mkdirs() }

    private fun file(context: Context, thread: String) =
        File(dir(context), thread.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    fun load(context: Context, thread: String): List<ChatMessage> {
        val f = file(context, thread)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChatMessage(
                    role = o.getString("r"),
                    content = o.getString("c"),
                    imagePath = o.optString("i", "").ifBlank { null },
                    ts = o.optLong("t", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, thread: String, messages: List<ChatMessage>) {
        val arr = JSONArray()
        messages.takeLast(KEEP).forEach { m ->
            val o = JSONObject().put("r", m.role).put("c", m.content).put("t", m.ts)
            m.imagePath?.let { o.put("i", it) }
            arr.put(o)
        }
        runCatching { file(context, thread).writeText(arr.toString()) }
    }

    fun clear(context: Context, thread: String) {
        file(context, thread).delete()
    }
}
