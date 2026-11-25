package app.kaori.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Przekierowania przy wklejonym linku. Najprostsza pułapka: publiczna strona odpowiada
 * `302 Location: http://192.168.1.1/admin` — jeśli nie sprawdzimy każdego skoku od nowa,
 * telefon sam pójdzie do routera w twojej sieci i przyniesie jego treść do promptu.
 */
class PageRedirectTest {

    private val base = "https://example.com/blog/post"

    private fun hop(location: String?, hops: Int = 0) =
        KaoriEngine.nextHop(base, location, hops)

    @Test
    fun absolutePathKeepsHost() {
        assertEquals("https://example.com/a/b", hop("/a/b"))
    }

    @Test
    fun relativePathResolvesAgainstCurrentPage() {
        assertEquals("https://example.com/blog/next", hop("next"))
    }

    @Test
    fun protocolRelativeFollowsTheCurrentScheme() {
        assertEquals("https://cdn.example.com/x", hop("//cdn.example.com/x"))
    }

    @Test
    fun anotherHttpsHostIsFine() {
        assertEquals("https://other.example.org/x", hop("https://other.example.org/x"))
    }

    @Test
    fun plainHttpIsRefused() {
        // Ten sam powód, co przy wklejonym linku: klucz nie jedzie po cleartext.
        assertNull(hop("http://example.com/x"))
    }

    @Test
    fun privateAddressIsRefused() {
        assertNull(hop("https://192.168.1.1/admin"))
    }

    @Test
    fun localhostIsRefused() {
        assertNull(hop("https://127.0.0.1:8080/"))
    }

    @Test
    fun lanNameIsRefused() {
        assertNull(hop("https://router.local/setup"))
    }

    @Test
    fun missingLocationIsNotAHop() {
        assertNull(hop(null))
        assertNull(hop("   "))
    }

    @Test
    fun loopStopsAtTheLimit() {
        // Bez limitu strona może przekierowywać w kółko i blokować pobieranie bez końca.
        assertNull(hop("/again", hops = 5))
        assertEquals("https://example.com/again", hop("/again", hops = 4))
    }
}
