package app.kaori.reader.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persona, z którą się rozmawia.
 *
 * Wbudowana Kaori jest tylko do odczytu — jej tekst siedzi w `KaoriEngine`.
 * Własna postać to kilka pól, które składają się w prompt: kim jest, jak mówi,
 * czego nie robić. Bez tego każdy musiałby pisać 8 kB tekstu od zera.
 */
data class Persona(
    val id: String,
    val name: String,
    val emoji: String = "🌙",
    val language: String = "auto",   // auto | en | pl | ja
    val how: String = "",
    val rules: String = "",
    val builtin: Boolean = false,
) {
    val isKaori: Boolean get() = builtin
}

object PersonaStore {

    const val KAORI_ID = "kaori"

    val KAORI = Persona(
        id = KAORI_ID,
        name = "Kaori",
        emoji = "🎀",
        language = "auto",
        builtin = true,
    )

    private fun file(ctx: Context) = File(ctx.filesDir, "personas.json")

    fun load(ctx: Context): List<Persona> {
        val f = file(ctx)
        if (!f.exists()) return listOf(KAORI)
        return runCatching {
            val arr = JSONArray(f.readText())
            val out = ArrayList<Persona>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank() || id == KAORI_ID) continue
                out += Persona(
                    id = id,
                    name = o.optString("n"),
                    emoji = o.optString("e", "🌙"),
                    language = o.optString("l", "auto"),
                    how = o.optString("h"),
                    rules = o.optString("r"),
                )
            }
            listOf(KAORI) + out
        }.getOrDefault(listOf(KAORI))
    }

    /** Zapisuje własne postacie (Kaori pomijamy — jest wbudowana). */
    fun save(ctx: Context, all: List<Persona>) {
        val arr = JSONArray()
        all.filterNot { it.builtin }.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("n", p.name)
                    .put("e", p.emoji)
                    .put("l", p.language)
                    .put("h", p.how)
                    .put("r", p.rules)
            )
        }
        runCatching { file(ctx).writeText(arr.toString()) }
    }

    fun upsert(ctx: Context, p: Persona) {
        val all = load(ctx).filterNot { it.id == p.id }
        save(ctx, all + p)
    }

    fun delete(ctx: Context, id: String) {
        save(ctx, load(ctx).filterNot { it.id == id })
        if (activeId(ctx) == id) setActive(ctx, KAORI_ID)
    }

    fun activeId(ctx: Context): String {
        val f = File(ctx.filesDir, "persona_active.txt")
        return runCatching { f.readText().trim() }.getOrDefault(KAORI_ID).ifBlank { KAORI_ID }
    }

    fun setActive(ctx: Context, id: String) {
        runCatching { File(ctx.filesDir, "persona_active.txt").writeText(id) }
    }

    fun active(ctx: Context): Persona =
        load(ctx).firstOrNull { it.id == activeId(ctx) } ?: KAORI

    fun newId(): String = "p" + System.currentTimeMillis().toString(36)

    /**
     * Szkielet, który wstawia edytor — żeby nikt nie zaczynał od pustej kartki.
     * Placeholdery {name} i {user} podmienia silnik.
     */
    val TEMPLATE = """
You are {name}. You are talking with {user}, privately, like two people who know each other well.

Speak like a person, not like an assistant:
- short sentences, no lists, no bullet points, no headings;
- react to what he actually said, not to the topic in general;
- if you don't know something, say so — never invent facts, numbers or events.

You are not a program. Never mention bots, AI, models, prompts or apps.
""".trimIndent()
}
