package app.kaori.reader.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Licznik czytania na dany dzień. */
data class DayStat(val pages: Int = 0, val minutes: Int = 0)

/**
 * Stats czytania trzymane lokalnie: ile stron i minut dziennie.
 * Zero chmury — to tylko licznik, nie warto wysyłać go na serwer.
 */
object StatsStore {

    private fun file(context: Context) = File(context.filesDir, "stats.json")

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())

    fun load(context: Context): Map<String, DayStat> {
        val f = file(context)
        if (!f.exists()) return emptyMap()
        return runCatching {
            val days = JSONObject(f.readText())
            days.keys().asSequence().associateWith { k ->
                val o = days.getJSONObject(k)
                DayStat(o.optInt("p"), o.optInt("m"))
            }
        }.getOrDefault(emptyMap())
    }

    fun addPages(context: Context, pages: Int) {
        if (pages <= 0) return
        mutate(context) { d -> d.copy(pages = d.pages + pages) }
    }

    fun addMinute(context: Context) {
        mutate(context) { d -> d.copy(minutes = d.minutes + 1) }
    }

    private fun mutate(context: Context, block: (DayStat) -> DayStat) {
        val all = load(context).toMutableMap()
        val day = today()
        all[day] = block(all[day] ?: DayStat())
        val obj = JSONObject()
        all.forEach { (d, s) -> obj.put(d, JSONObject().put("p", s.pages).put("m", s.minutes)) }
        runCatching { AtomicJson.write(file(context), obj.toString()) }
    }

    /** Ile dni z rzędu (łącznie z today) było czytanie. */
    fun streak(context: Context): Int {
        val all = load(context)
        if (all.isEmpty()) return 0
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        var day = System.currentTimeMillis()
        var count = 0
        // jeśli today jeszcze nic nie ma, liczymy od yesterday
        if ((all[fmt.format(Date(day))]?.pages ?: 0) <= 0) day -= 86_400_000L
        while (true) {
            val key = fmt.format(Date(day))
            val s = all[key] ?: break
            if (s.pages <= 0 && s.minutes <= 0) break
            count++
            day -= 86_400_000L
        }
        return count
    }
}
