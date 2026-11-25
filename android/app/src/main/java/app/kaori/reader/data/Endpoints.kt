package app.kaori.reader.data

import java.net.URI

/**
 * Adres API: `https` wszędzie, `http` tylko do własnej sieci.
 *
 * Po co blokada w kodzie, skoro manifest i tak dopuszcza cleartext: Android nie umie
 * wpisać w `network_security_config` zakresu adresów (10.x, 192.168.x, 172.16–31.x —
 * tam działają wyłącznie nazwy domen). Więc cleartext jest dopuszczony globalnie,
 * a tutaj pilnujemy, żeby po `http` dało się gadać **wyłącznie** z adresem prywatnym.
 * Lokalny serwer (LM Studio, Ollama, LocalAI, proxy) działa, publiczny dostawca po
 * `http` jest odrzucany, zanim cokolwiek wyjdzie w sieć.
 */
object Endpoints {

    /** @param plainHttpLocal true, gdy adres jest w sieci lokalnej i leci bez szyfrowania. */
    data class Verdict(
        val ok: Boolean,
        val plainHttpLocal: Boolean,
        val problem: String? = null,
    )

    fun check(url: String): Verdict {
        val raw = url.trim()
        if (raw.isEmpty()) return Verdict(false, false, "the address is empty")

        val uri = runCatching { URI(raw) }.getOrNull()
            ?: return Verdict(false, false, "that is not a valid address")
        val scheme = uri.scheme?.lowercase()
            ?: return Verdict(
                false,
                false,
                "start the address with https:// (http:// only for your own network)",
            )
        val host = uri.host.orEmpty()
        if (host.isBlank()) return Verdict(false, false, "that is not a valid address")

        return when (scheme) {
            "https" -> Verdict(true, false)
            "http" -> if (isLocalHost(host)) {
                Verdict(true, true)
            } else {
                Verdict(
                    false,
                    false,
                    "plain http is only for your own network " +
                        "(localhost, 10.x, 192.168.x, 172.16-31.x, *.local) — use https:// otherwise",
                )
            }
            else -> Verdict(false, false, "only http:// and https:// addresses work")
        }
    }

    /**
     * Czy wolno pobrać stronę z wklejonego linku. Tu jest ostrzej niż przy API:
     * tylko `https`, i nigdy adres prywatny — inaczej wklejony `http://192.168.1.1/…`
     * pozwoliłby jej czytać router albo NAS w domu.
     */
    fun checkFetch(url: String): Verdict {
        val raw = url.trim()
        if (raw.isEmpty()) return Verdict(false, false, "the link is empty")
        val uri = runCatching { URI(raw) }.getOrNull()
            ?: return Verdict(false, false, "that is not a valid link")
        val scheme = uri.scheme?.lowercase()
            ?: return Verdict(false, false, "the link has no https:// prefix")
        val host = uri.host.orEmpty()
        if (host.isBlank()) return Verdict(false, false, "that is not a valid link")
        if (scheme != "https") {
            return Verdict(false, false, "only https:// links can be opened — that one is $scheme")
        }
        if (isLocalHost(host)) {
            return Verdict(false, false, "links to your own network are not fetched")
        }
        return Verdict(true, false)
    }

    /**
     * Czy host jest w sieci lokalnej: `localhost`, `*.local`, pętla zwrotna,
     * `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, link-local `169.254.0.0/16`.
     */
    fun isLocalHost(host: String): Boolean {
        val h = host.lowercase().removeSurrounding("[", "]").trimEnd('.')
        if (h == "localhost" || h == "::1" || h == "0.0.0.0") return true
        if (h.endsWith(".local") || h.endsWith(".localhost")) return true
        if (h.endsWith(".lan") || h.endsWith(".home.arpa")) return true

        val parts = h.split('.')
        if (parts.size != 4) return false
        val nums = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return false }
        val a = nums[0]
        val b = nums[1]
        return a == 127 ||
            a == 10 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31) ||
            (a == 169 && b == 254)
    }
}
