package app.kaori.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Koszt tokenów i dzielenie rozdziału — to od tego zależy, czy obietnica
 * „to weźmie ~12 000 tokenów" jest prawdziwa i czy odpowiedź się nie urwie.
 */
class TextChunksTest {

    @Test
    fun `estimate counts ascii as four chars per token`() {
        val text = "a".repeat(400)
        assertEquals(101, TextChunks.estimateTokens(text))
    }

    @Test
    fun `estimate counts cjk almost one to one`() {
        val text = "世界".repeat(100)
        assertEquals(201, TextChunks.estimateTokens(text))
    }

    @Test
    fun `estimate is never zero`() {
        assertEquals(1, TextChunks.estimateTokens(""))
    }

    @Test
    fun `short text stays in one chunk`() {
        val parts = TextChunks.split("Hello there.\n\nSecond paragraph.")
        assertEquals(1, parts.size)
    }

    @Test
    fun `long text is split below the limit`() {
        val para = "Sentence number one is here. ".repeat(300) // ~8400 znaków
        val parts = TextChunks.split("$para\n\n$para\n\n$para")
        assertTrue("musi być więcej niż jeden kawałek", parts.size > 1)
        assertTrue("każdy kawałek <= 6000 znaków", parts.all { it.length <= 6000 })
    }

    @Test
    fun `single huge paragraph is cut on sentence boundary`() {
        val text = (1..200).joinToString(" ") { "This is sentence $it." }
        val parts = TextChunks.split(text, limit = 300)
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.length <= 300 })
        // nie może zgubić ani jednego zdania
        val joined = parts.joinToString(" ")
        (1..200).forEach { assertTrue(joined.contains("sentence $it.")) }
    }

    @Test
    fun `splitting keeps every paragraph`() {
        val text = (1..50).joinToString("\n\n") { "Paragraph $it body text." }
        val parts = TextChunks.split(text, limit = 200)
        val joined = parts.joinToString("\n\n")
        (1..50).forEach { assertTrue(joined.contains("Paragraph $it body text.")) }
    }
}
