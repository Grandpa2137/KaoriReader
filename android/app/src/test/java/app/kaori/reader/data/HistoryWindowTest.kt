package app.kaori.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ile historii jedzie do modelu i co się dzieje, gdy to za dużo.
 *
 * Dwie rzeczy tu bolą: ucięta rozmowa w środku zdania (za mały budżet) i odpowiedź „API error (400)”
 * zamiast odpowiedzi Kaori, bo historia nie zmieściła się w oknie modelu. Test pilnuje obu.
 */
class HistoryWindowTest {

    private fun msg(role: String, text: String) = ChatMessage(role, text, ts = 0L)

    private fun window(budget: Int, history: List<ChatMessage>) =
        KaoriEngine.windowOf(history, budget)

    @Test
    fun shortHistoryIsSentWhole() {
        val history = listOf(msg("user", "hi"), msg("assistant", "hey"))
        assertEquals(2, window(16_000, history).size)
    }

    @Test
    fun smallBudgetKeepsTheNewestMessages() {
        val history = (1..40).map { msg(if (it % 2 == 0) "assistant" else "user", "x".repeat(500)) }
        val kept = window(3_000, history)
        // Zostają najnowsze, nie najstarsze — inaczej Kaori odpowiada na zdanie sprzed godziny.
        assertEquals(history.last().content, kept.last().content)
        assertTrue("budżet niepilnowany: ${kept.size}", kept.size < history.size)
    }

    @Test
    fun minimumMessagesAlwaysFit() {
        // Jedna ogromna wiadomość nie może wyciąć całej rozmowy.
        val history = (1..10).map { msg("user", "y".repeat(4_000)) }
        assertTrue(window(1_000, history).size >= 8)
    }

    @Test
    fun emptyHistoryStaysEmpty() {
        assertTrue(window(16_000, emptyList()).isEmpty())
    }

    @Test
    fun contextOverflowIsRecognised() {
        assertTrue(KaoriEngine.isContextOverflow("API error (400): This model's maximum context length is 65536 tokens"))
        assertTrue(KaoriEngine.isContextOverflow("API error (400): context_length_exceeded"))
        assertTrue(KaoriEngine.isContextOverflow("API error (413): request too long"))
    }

    @Test
    fun otherErrorsAreNotOverflow() {
        // Klucza nie wolno „naprawiać” połówką historii — to nie ten problem.
        assertFalse(KaoriEngine.isContextOverflow("API error (401): Authentication Fails"))
        assertFalse(KaoriEngine.isContextOverflow("API error (429): Too Many Requests"))
        assertFalse(KaoriEngine.isContextOverflow("Can't connect: timeout"))
    }
}
