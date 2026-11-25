package app.kaori.reader.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Wykrywanie języka ostatniej wiadomości. Od tego zależy, czy Kaori odpowie po
 * angielsku na angielskie pytanie, czy znowu zjedzie na polski z przyzwyczajenia.
 */
class LangDetectTest {

    @Test
    fun `plain english is recognised`() {
        listOf(
            "what are you doing right now",
            "i'm at work, this shift is killing me",
            "do you really think that is a good idea",
            "the book is better than the show and you know it",
        ).forEach { assertEquals(it, "en", KaoriEngine.detectLang(it)) }
    }

    @Test
    fun `polish is recognised by words and diacritics`() {
        listOf(
            "co robisz teraz",
            "nie wiem jak to jest możliwe",
            "jestem w pracy, ta zmiana mnie wykańcza",
            "zażółć gęślą jaźń",
        ).forEach { assertEquals(it, "pl", KaoriEngine.detectLang(it)) }
    }

    @Test
    fun `japanese is recognised by kana`() {
        assertEquals("ja", KaoriEngine.detectLang("いま何してるの"))
        assertEquals("ja", KaoriEngine.detectLang("カオリ、元気？"))
    }

    @Test
    fun `too little signal means no forced language`() {
        listOf("", "   ", "ok", "lol", "??", "123").forEach {
            assertEquals("'$it' nie powinno narzucać języka", "", KaoriEngine.detectLang(it))
        }
    }

    @Test
    fun `single polish word in an english sentence does not flip it`() {
        assertEquals(
            "en",
            KaoriEngine.detectLang("that is what you said yesterday about the whole thing"),
        )
    }
}
