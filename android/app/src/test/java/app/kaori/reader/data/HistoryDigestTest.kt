package app.kaori.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Skrót tego, co wypadło z okna rozmowy. Bez niego Kaori po trzydziestu wiadomościach pyta
 * drugi raz, czy jestem w pracy — a to jedyna rzecz, której naprawdę nie wolno zgubić.
 */
class HistoryDigestTest {

    private fun msg(role: String, text: String) = ChatMessage(role, text, ts = 0L)

    @Test
    fun nothingDroppedMeansNoDigest() {
        assertEquals("", KaoriEngine.digestOf(emptyList()))
    }

    @Test
    fun keepsWhatWasSaidAndWhoSaidIt() {
        val dropped = listOf(msg("user", "jestem w pracy"), msg("assistant", "no to powodzenia"))
        val digest = KaoriEngine.digestOf(dropped)
        assertTrue(digest.contains("HE: jestem w pracy"))
        assertTrue(digest.contains("KAORI: no to powodzenia"))
    }

    @Test
    fun keepsChronologicalOrder() {
        val dropped = listOf(msg("user", "pierwsze"), msg("assistant", "drugie"), msg("user", "trzecie"))
        val lines = KaoriEngine.digestOf(dropped).lines()
        assertEquals("HE: pierwsze", lines[0])
        assertEquals("HE: trzecie", lines[2])
    }

    @Test
    fun newestDroppedMessagesWin() {
        // Gdy skrót nie mieści wszystkiego, ważniejsze jest to, co było tuż przed oknem.
        val dropped = (1..50).map { msg("user", "wiadomosc numer $it") }
        val digest = KaoriEngine.digestOf(dropped, budget = 300)
        assertTrue("brak najnowszej", digest.contains("numer 50"))
        assertTrue("powinno być krótko", digest.length <= 400)
        assertTrue("nie powinno zmieścić wszystkiego", !digest.contains("numer 1\n"))
    }

    @Test
    fun longMessagesAreTruncated() {
        val digest = KaoriEngine.digestOf(listOf(msg("user", "x".repeat(2_000))))
        assertTrue("nie przycięto: ${digest.length}", digest.length <= 310)
    }

    @Test
    fun whitespaceIsSquashedSoLinesStayReadable() {
        val digest = KaoriEngine.digestOf(listOf(msg("user", "linia\n\n\n  druga")))
        assertEquals("HE: linia druga", digest)
    }
}
