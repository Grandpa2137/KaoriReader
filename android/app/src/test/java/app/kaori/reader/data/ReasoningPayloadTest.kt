package app.kaori.reader.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rozumowanie ma różne nazwy u różnych dostawców: DeepSeek chce `thinking`, OpenRouter `reasoning`.
 * Wysłanie cudzego pola kończy się błędem 400, a niewysłanie żadnego oznacza, że przełącznik
 * w Ustawieniach kłamie. Ten test pilnuje obu rzeczy.
 */
class ReasoningPayloadTest {

    private fun body(config: LlmConfig, allowReasoning: Boolean = true, thinking: Boolean = true): JSONObject {
        val cfg = if (config.thinking == thinking) config else config.copy(thinking = thinking)
        return with(KaoriEngine) { JSONObject().withThinking(cfg, allowReasoning) }
    }

    private val deepseek = LlmConfig("https://api.deepseek.com", "deepseek-chat", thinking = true)
    private val openrouter = LlmConfig("https://openrouter.ai/api/v1", "deepseek/deepseek-chat", thinking = true)
    private val openai = LlmConfig("https://api.openai.com/v1", "gpt-4o-mini", thinking = true)
    private val local = LlmConfig("http://127.0.0.1:11434/v1", "llama3", thinking = true)

    @Test
    fun deepSeekGetsItsOwnField() {
        val b = body(deepseek)
        assertEquals("enabled", b.getJSONObject("thinking").getString("type"))
        assertFalse(b.has("reasoning"))
    }

    @Test
    fun deepSeekSwitchOffIsSentAsDisabled() {
        val b = body(deepseek, thinking = false)
        assertEquals("disabled", b.getJSONObject("thinking").getString("type"))
    }

    @Test
    fun translationStaysCheapEvenWhenThinkingIsOn() {
        // allowReasoning=false to tłumaczenie i pamięć — nie ma tam czego myśleć.
        val b = body(deepseek, allowReasoning = false)
        assertEquals("disabled", b.getJSONObject("thinking").getString("type"))
    }

    @Test
    fun openRouterGetsReasoningField() {
        val b = body(openrouter)
        assertTrue(b.getJSONObject("reasoning").getBoolean("enabled"))
        assertFalse(b.has("thinking"))
    }

    @Test
    fun openRouterSwitchOffDisablesReasoning() {
        val b = body(openrouter, thinking = false)
        assertFalse(b.getJSONObject("reasoning").getBoolean("enabled"))
    }

    @Test
    fun openAiGetsNothingAndNoSwitch() {
        val b = body(openai)
        assertFalse(b.has("thinking"))
        assertFalse(b.has("reasoning"))
        assertFalse(openai.supportsThinking)
    }

    @Test
    fun localServerGetsNothingAndNoSwitch() {
        assertFalse(body(local).has("thinking"))
        assertFalse(local.supportsThinking)
    }

    @Test
    fun stylesAreDetectedFromTheAddress() {
        assertEquals(ReasoningStyle.DeepSeek, deepseek.reasoningStyle)
        assertEquals(ReasoningStyle.OpenRouter, openrouter.reasoningStyle)
        assertEquals(ReasoningStyle.None, openai.reasoningStyle)
        assertEquals(ReasoningStyle.None, local.reasoningStyle)
        // Adres podany z dużej litery nie może wyłączyć funkcji.
        assertTrue(LlmConfig("https://API.DEEPSEEK.com/v1").supportsThinking)
    }

    @Test
    fun openRouterWithDeepSeekModelStillUsesTheOpenRouterField() {
        // Model jest deepseekowy, ale rozmawiamy przez OpenRoutera — liczy się dostawca,
        // bo to on waliduje pola żądania.
        assertEquals(ReasoningStyle.OpenRouter, openrouter.reasoningStyle)
        assertFalse(body(openrouter).has("thinking"))
    }
}
