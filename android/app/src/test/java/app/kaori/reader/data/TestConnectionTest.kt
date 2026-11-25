package app.kaori.reader.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test klucza to jedyna rzecz, która ma odpowiedzieć „działa / nie działa" zanim użytkownik
 * zacznie rozmowę. Mapowanie odpowiedzi dostawcy na zdanie po angielsku jest tu sprawdzane
 * bez sieci — sztuczne odpowiedzi zamiast prawdziwych, bo dostawcy zmieniają treść błędów,
 * a my chcemy pilnować własnej logiki, nie ich.
 */
class TestConnectionTest {

    private fun verdict(raw: String, model: String = "deepseek-chat") =
        KaoriEngine.testVerdict(raw, model)

    @Test
    fun successIsReportedWithModelName() {
        assertEquals("Connected · deepseek-chat", verdict("Hello!"))
    }

    @Test
    fun successUsesWhateverModelWasAsked() {
        assertEquals("Connected · gpt-4o-mini", verdict("ok", "gpt-4o-mini"))
    }

    @Test
    fun rejectedKey401() {
        val msg = verdict("""API error (401): {"error":{"message":"Authentication Fails"}}""")
        assertTrue(msg.startsWith("Key rejected by the provider (401)"))
    }

    @Test
    fun rejectedKeyInPlainWords() {
        val msg = verdict("API error (401): Unauthorized")
        assertTrue(msg.startsWith("Key rejected by the provider (401)"))
    }

    @Test
    fun rejectedKeyOpenAiStyle() {
        val msg = verdict("""API error (401): {"error":{"code":"invalid_api_key"}}""")
        assertTrue(msg.startsWith("Key rejected by the provider (401)"))
    }

    @Test
    fun noCredit402() {
        val msg = verdict("""API error (402): {"error":{"message":"Insufficient Balance"}}""")
        assertEquals("The key works, but there is no credit on that account.", msg)
    }

    @Test
    fun emptyBalanceIsNotReportedAsBrokenKey() {
        // Najgorszy błąd w tej funkcji: kazać komuś zmieniać dobry klucz, gdy nie ma środków.
        val msg = verdict("API error (400): balance is empty")
        assertEquals("The key works, but there is no credit on that account.", msg)
    }

    @Test
    fun wrongAddressOrModel404() {
        val msg = verdict("API error (404): Not Found")
        assertTrue(msg.startsWith("Address or model not found (404)"))
    }

    @Test
    fun wrongModelNameDeepSeekStyle() {
        val msg = verdict("""API error (400): {"error":{"message":"Model Not Exist"}}""")
        assertTrue(msg.startsWith("Address or model not found"))
    }

    @Test
    fun rateLimit429() {
        assertEquals(
            "Rate limited (429). The key works, wait a moment.",
            verdict("API error (429): Too Many Requests"),
        )
    }

    @Test
    fun refusedAddressIsPassedThroughUntouched() {
        val raw = "Can't connect: this address uses plain http. Android blocks it — use an https:// endpoint"
        assertEquals(raw, verdict(raw))
    }

    @Test
    fun unknownApiErrorIsShownAsIs() {
        val raw = "API error (500): internal server error"
        assertEquals(raw, verdict(raw))
    }

    @Test
    fun emptyKeyIsCaughtBeforeAnyRequest() = runBlocking {
        val msg = KaoriEngine.testConnection("", LlmConfig())
        assertEquals("Add your key first — the field above is empty.", msg)
    }

    @Test
    fun plainHttpToTheInternetNeverGetsTheKey() = runBlocking {
        // Najważniejszy test tego pliku: http do publicznego adresu ma zostać odrzucony
        // lokalnie, zanim cokolwiek poleci w sieć. Gdyby tego zabrakło, przycisk
        // „Test connection" wysłałby klucz otwartym tekstem.
        val cfg = LlmConfig(baseUrl = "http://example.com/v1")
        val msg = KaoriEngine.testConnection("sk-test-key", cfg)
        assertTrue("oczekiwano odmowy, dostałem: $msg", msg.startsWith("Can't connect"))
        assertTrue(msg.contains("http", ignoreCase = true))
    }

    @Test
    fun localMockServerIsStillAllowed() = runBlocking {
        // http do localhost jest dozwolony (Ollama, LM Studio). Tu sprawdzamy tylko,
        // że odmowa nie jest bezwarunkowa — komunikat dotyczy połączenia, nie polityki.
        val cfg = LlmConfig(baseUrl = "http://127.0.0.1:11434/v1")
        val msg = KaoriEngine.testConnection("key", cfg)
        assertTrue("nie powinno być komunikatu o http: $msg", !msg.contains("plain http"))
    }
}
