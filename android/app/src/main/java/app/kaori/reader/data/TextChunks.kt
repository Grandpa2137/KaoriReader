package app.kaori.reader.data

/**
 * Liczenie kosztu i dzielenie tekstu — czysta logika, bez Androida i bez sieci.
 *
 * Wydzielone z `KaoriEngine`, żeby dało się to przetestować na JVM: to od tych
 * dwóch funkcji zależy, czy „to weźmie ~12 000 tokenów" jest prawdą i czy długi
 * rozdział nie zostanie ucięty w połowie.
 */
object TextChunks {

    /**
     * Gruby szacunek: ASCII to ~4 znaki na token, pismo CJK prawie 1:1.
     * To nie licznik dostawcy — ma tylko powiedzieć „mniej więcej tyle".
     */
    fun estimateTokens(text: String): Int {
        var ascii = 0
        var wide = 0
        for (ch in text) if (ch.code < 128) ascii++ else wide++
        return ascii / 4 + wide + 1
    }

    /** Dzieli tekst po akapitach; każdy kawałek <= [limit] znaków. */
    fun split(text: String, limit: Int = 6000): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (para in text.split("\n\n")) {
            if (sb.isNotEmpty() && sb.length + para.length + 2 > limit) {
                out.add(sb.toString())
                sb.setLength(0)
            }
            if (para.length > limit) {
                // pojedynczy moloch: tniemy po zdaniach, a jak i to nie wyjdzie — na twardo
                var rest = para
                while (rest.length > limit) {
                    var cut = rest.lastIndexOf('.', limit)
                    if (cut < limit / 2) cut = limit - 1
                    out.add(rest.substring(0, cut + 1).trim())
                    rest = rest.substring(cut + 1)
                }
                if (rest.isNotBlank()) sb.append(rest).append("\n\n")
                continue
            }
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(para)
        }
        if (sb.isNotBlank()) out.add(sb.toString())
        return out.ifEmpty { listOf(text) }
    }
}
