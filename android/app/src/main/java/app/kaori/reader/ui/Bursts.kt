package app.kaori.reader.ui

/**
 * Dzieli odpowiedź Kaori na 1-3 wiadomości po liniach.
 *
 * Jedna wiadomość = jedno akapicie to największy „bot tell" w czacie. Człowiek
 * wysyła myśl po myśli, z pauzą. Pusta albo jednolinijkowa odpowiedź zostaje
 * w całości — dzielenie „ok" na dwie wiadomości wyglądałoby gorzej niż brak.
 */
internal fun splitBursts(text: String, maxParts: Int = 3): List<String> {
    val t = text.trim()
    if (t.isEmpty()) return listOf(t)
    val lines = t.split("\n")
    val nonEmpty = lines.count { it.isNotBlank() }
    if (nonEmpty < 2) return listOf(t)

    val n = minOf(maxParts, maxOf(2, (nonEmpty + 1) / 2))
    val target = maxOf(1, nonEmpty / n)

    val chunks = mutableListOf<String>()
    val cur = StringBuilder()
    var seen = 0
    for (line in lines) {
        if (cur.isNotEmpty()) cur.append('\n')
        cur.append(line)
        if (line.isNotBlank()) seen++
        if (seen >= target && chunks.size < n - 1) {
            chunks += cur.toString().trim()
            cur.setLength(0)
            seen = 0
        }
    }
    val tail = cur.toString().trim()
    if (tail.isNotEmpty()) chunks += tail
    val out = chunks.filter { it.isNotEmpty() }
    return if (out.size > 1) out else listOf(t)
}
