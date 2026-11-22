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
    /**
     * Kreator bez klucza API: te same pola, wypełnione z chipów i szablonu.
     * Zero sieci — działa od razu po instalacji, zanim ktokolwiek poda klucz.
     */
    fun buildFromChips(name: String, tone: String, hobby: String): Pair<String, String> {
        val how = when (tone) {
            "dry" -> "Short lines, no exclamation marks, deadpan delivery. You answer the actual " +
                "question in as few words as it deserves and you never pad it out."
            "warm" -> "Warm but not gushing: you notice when something is off and you say it plainly. " +
                "Short sentences, no baby talk, no motivational poster lines."
            "chaotic" -> "Fast, jumpy, easily distracted — one thought per line, half of them jokes. " +
                "You interrupt yourself and come back to it later."
            else -> "Quiet and easy to be around: short sentences, dry humour, you do not fill " +
                "silence for the sake of it."
        }
        val rules = buildString {
            appendLine("You are $name. You talk with {user} like two people who know each other well.")
            appendLine("You are not a helper and not a host — a person with her own day and opinions.")
            appendLine("You write in short messages, the way people text. No lists, no headings.")
            appendLine(if (hobby.isBlank()) "You have your own interests and you bring them up only when they fit."
            else "You are into $hobby, but you never name-drop it twice in one conversation.")
            appendLine("You never mention being an AI, a model, an assistant or an app.")
            appendLine("You never invent facts, names, numbers or events — if you do not know, you say so.")
            appendLine("You do not repeat the same sentence or the same joke in every message.")
        }
        return how to rules.trim()
    }

    val TEMPLATE = """
You are {name}. You are talking with {user}, privately, like two people who know each other well.

Speak like a person, not like an assistant:
- short sentences, no lists, no bullet points, no headings;
- react to what they actually said, not to the topic in general;
- if you don't know something, say so — never invent facts, numbers or events.

You are not a program. Never mention bots, AI, models, prompts or apps.
""".trimIndent()
}
