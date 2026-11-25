package app.kaori.reader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reguła: https wszędzie, http tylko do własnej sieci. Bez tego testu literówka
 * w rozpoznawaniu adresów prywatnych wypuszcza klucz API po cleartext do internetu.
 */
class EndpointsTest {

    @Test
    fun `https is always fine`() {
        listOf(
            "https://api.deepseek.com",
            "https://api.openai.com/v1",
            "https://192.168.0.10:8443/v1",
            "https://example.com/chat/completions",
        ).forEach {
            val v = Endpoints.check(it)
            assertTrue("$it powinno przejść", v.ok)
            assertFalse("$it nie jest cleartextem", v.plainHttpLocal)
        }
    }

    @Test
    fun `http to a private address is allowed but flagged`() {
        listOf(
            "http://localhost:11434/v1",
            "http://127.0.0.1:1234/v1",
            "http://10.0.0.5:8000",
            "http://10.255.255.1",
            "http://192.168.0.74:8099/v1",
            "http://172.16.0.1",
            "http://172.31.255.254:8080",
            "http://169.254.10.10",
            "http://nas.local/v1",
            "http://ollama.localhost:11434",
            "http://[::1]:8080",
        ).forEach {
            val v = Endpoints.check(it)
            assertTrue("$it powinno przejść", v.ok)
            assertTrue("$it ma być oznaczone jako cleartext", v.plainHttpLocal)
        }
    }

    @Test
    fun `http to the internet is refused`() {
        listOf(
            "http://api.deepseek.com",
            "http://example.com/v1",
            "http://8.8.8.8",
            "http://172.32.0.1",   // tuż za zakresem 172.16-31
            "http://172.15.0.1",   // tuż przed
            "http://11.0.0.1",
            "http://192.169.0.1",
            "http://127.0.0.256",  // nie jest poprawnym adresem
        ).forEach {
            val v = Endpoints.check(it)
            assertFalse("$it musi być odrzucone", v.ok)
            assertTrue("$it ma dostać powód", !v.problem.isNullOrBlank())
        }
    }

    @Test
    fun `garbage and empty input have a reason`() {
        listOf("", "   ", "api.deepseek.com", "ftp://example.com", "htp://x").forEach {
            val v = Endpoints.check(it)
            assertFalse("'$it' nie może przejść", v.ok)
            assertTrue(!v.problem.isNullOrBlank())
        }
    }

    @Test
    fun `local detection ignores case and trailing dot`() {
        assertTrue(Endpoints.isLocalHost("LOCALHOST"))
        assertTrue(Endpoints.isLocalHost("box.LOCAL."))
        assertFalse(Endpoints.isLocalHost("notlocal.com"))
        assertFalse(Endpoints.isLocalHost("10.0.0"))   // brakuje oktetu
        assertFalse(Endpoints.isLocalHost("192.168.0.a"))
    }

    @Test
    fun `fetched links must be https and outside the LAN`() {
        assertTrue(Endpoints.checkFetch("https://en.wikipedia.org/wiki/Light_novel").ok)
        assertTrue(Endpoints.checkFetch("https://example.com/article").ok)

        // http nigdy — nawet do własnej sieci: inaczej wklejony link czyta router/NAS
        listOf(
            "http://example.com",
            "http://192.168.1.1/admin",
            "http://localhost:8080",
        ).forEach {
            val v = Endpoints.checkFetch(it)
            assertFalse("$it nie może być pobierany", v.ok)
            assertTrue(!v.problem.isNullOrBlank())
        }

        // https do adresu prywatnego też nie — to nadal skanowanie domu
        listOf(
            "https://192.168.1.1",
            "https://10.0.0.5:8443",
            "https://nas.local",
        ).forEach {
            assertFalse("$it nie może być pobierany", Endpoints.checkFetch(it).ok)
        }
    }
}
