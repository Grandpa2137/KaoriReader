package app.kaori.reader.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Fakt o użytkowniku: „praca → zmiany 24h". Klucz jest stałą, wartość się zmienia. */
data class Fact(val key: String, val value: String, val at: Long = 0L)

/** Otwarty wątek rozmowy — to, co „wisi" między nimi. */
data class Thread(val topic: String, val state: String, val at: Long = 0L)

/** Streszczenie jednej rozmowy. */
data class Episode(val summary: String, val at: Long, val importance: Int, val keys: List<String>)

/**
 * Memory trzymana WYŁĄCZNIE na telefonie, osobno dla każdej postaci.
 *
 * Trzy warstwy, bo sama lista streszczeń nie odpowiada na „co u mnie słychać":
 * - fakty (ustrukturyzowane, zawsze w promptcie),
 * - wątki (co jest niedokończone),
 * - streszczenia (po nich szukamy słowami).
 *
 * Świadomie bez wektorów: BM25 po słowach + świeżość + ważność wystarcza,
 * a nie ciągnie ze sobą 25 MB modelu ani sieci.
 */
object MemoryStore {

    private fun dir(ctx: Context, personaId: String): File =
        File(File(ctx.filesDir, "memory"), personaId).apply { mkdirs() }

    private fun factsFile(ctx: Context, p: String) = File(dir(ctx, p), "facts.json")
    private fun threadsFile(ctx: Context, p: String) = File(dir(ctx, p), "threads.json")
    private fun episodesFile(ctx: Context, p: String) = File(dir(ctx, p), "episodes.json")

    // ------------------------------------------------------------------ odczyt

    fun facts(ctx: Context, personaId: String): List<Fact> = runCatching {
        val arr = JSONArray(factsFile(ctx, personaId).readText())
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                Fact(it.optString("k"), it.optString("v"), it.optLong("t"))
            }?.takeIf { f -> f.key.isNotBlank() && f.value.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    fun threads(ctx: Context, personaId: String): List<Thread> = runCatching {
        val arr = JSONArray(threadsFile(ctx, personaId).readText())
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                Thread(it.optString("t"), it.optString("s"), it.optLong("a"))
            }?.takeIf { t -> t.topic.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    fun episodes(ctx: Context, personaId: String): List<Episode> = runCatching {
        val arr = JSONArray(episodesFile(ctx, personaId).readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val keys = ArrayList<String>()
            o.optJSONArray("k")?.let { a -> for (j in 0 until a.length()) keys += a.optString(j) }
            Episode(o.optString("s"), o.optLong("t"), o.optInt("i", 2), keys)
                .takeIf { it.summary.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    fun counts(ctx: Context, personaId: String): Triple<Int, Int, Int> =
        Triple(facts(ctx, personaId).size, threads(ctx, personaId).size, episodes(ctx, personaId).size)

    // ------------------------------------------------------------------ zapis

    fun saveFacts(ctx: Context, personaId: String, list: List<Fact>) = runCatching {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("k", it.key).put("v", it.value).put("t", it.at)) }
        AtomicJson.write(factsFile(ctx, personaId), arr.toString())
    }

    fun saveThreads(ctx: Context, personaId: String, list: List<Thread>) = runCatching {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.topic).put("s", it.state).put("a", it.at)) }
        AtomicJson.write(threadsFile(ctx, personaId), arr.toString())
    }

    fun saveEpisodes(ctx: Context, personaId: String, list: List<Episode>) = runCatching {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("s", e.summary)
                    .put("t", e.at)
                    .put("i", e.importance)
                    .put("k", JSONArray(e.keys))
            )
        }
        AtomicJson.write(episodesFile(ctx, personaId), arr.toString())
    }

    fun deleteFact(ctx: Context, personaId: String, key: String) =
        saveFacts(ctx, personaId, facts(ctx, personaId).filterNot { it.key == key })

    fun deleteEpisode(ctx: Context, personaId: String, at: Long) =
        saveEpisodes(ctx, personaId, episodes(ctx, personaId).filterNot { it.at == at })

    fun deleteThread(ctx: Context, personaId: String, topic: String) =
        saveThreads(ctx, personaId, threads(ctx, personaId).filterNot { it.topic == topic })

    fun wipe(ctx: Context, personaId: String) {
        runCatching { dir(ctx, personaId).deleteRecursively() }
    }

    // ------------------------------------------------------------------ scalanie

    /** Fakt o tym samym kluczu zastępuje stary — historia nie jest potrzebna. */
    fun mergeFact(ctx: Context, personaId: String, key: String, value: String) {
        val all = facts(ctx, personaId).toMutableList()
        val i = all.indexOfFirst { it.key.equals(key, ignoreCase = true) }
        val now = System.currentTimeMillis()
        if (i >= 0) all[i] = Fact(all[i].key, value, now) else all += Fact(key, value, now)
        saveFacts(ctx, personaId, if (all.size > 120) all.sortedByDescending { it.at }.take(120) else all)
    }

    fun mergeThread(ctx: Context, personaId: String, topic: String, state: String) {
        val all = threads(ctx, personaId).toMutableList()
        val i = all.indexOfFirst { it.topic.equals(topic, ignoreCase = true) }
        val now = System.currentTimeMillis()
        if (i >= 0) all[i] = Thread(all[i].topic, state, now) else all += Thread(topic, state, now)
        saveThreads(ctx, personaId, if (all.size > 40) all.sortedByDescending { it.at }.take(40) else all)
    }

    fun addEpisode(ctx: Context, personaId: String, e: Episode) {
        val all = (episodes(ctx, personaId) + e).sortedByDescending { it.at }
        saveEpisodes(ctx, personaId, all.take(400))
    }

    // ------------------------------------------------------------------ odpytywanie

    /**
     * Co trafia do promptu: all fakty (do 18), świeże wątki (7 dni, do 4)
     * i najlepiej dopasowane streszczenia (do 4).
     */
    fun recall(ctx: Context, personaId: String, query: String, maxFacts: Int = 18): List<String> {
        val out = ArrayList<String>()
        facts(ctx, personaId)
            .sortedByDescending { it.at }
            .take(maxFacts)
            .forEach { out += "${it.key}: ${it.value}" }

        val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        threads(ctx, personaId)
            .filter { it.at >= weekAgo }
            .sortedByDescending { it.at }
            .take(4)
            .forEach { out += "open thread — ${it.topic}: ${it.state}" }

        val words = query.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 4 }
            .distinct()
        val eps = episodes(ctx, personaId)
        val scored = eps.map { e ->
            val text = (e.summary + " " + e.keys.joinToString(" ")).lowercase()
            var score = 0.0
            words.forEach { w -> if (text.contains(w)) score += 1.0 + 0.25 * text.split(w).size }
            // świeżość i ważność jako dodatek, nie jako wyznacznik
            val ageDays = ((System.currentTimeMillis() - e.at) / 86_400_000L).coerceAtLeast(0)
            score += (5 - e.importance).coerceAtLeast(0) * 0.1 + (7 - ageDays).coerceAtLeast(0) * 0.05
            e to score
        }.sortedByDescending { it.second }

        val anyMatches = scored.any { it.second > 0.4 }
        scored
            .take(if (anyMatches) 4 else 2)
            .forEach { out += it.first.summary }

        return out
    }
}
