package app.kaori.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Regresja na scenariusz z recenzji: uszkodzony plik nie może zostać nadpisany pustką,
 * a raz zapisany postęp musi wracać w całości (razem z przeczytanymi zakresami).
 */
class ProgressStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file() = File(tmp.root, "progress.json")

    private fun entry(chapter: Int, read: List<IntRange>) = ReadingProgress(
        chapter = chapter,
        addr = 123,
        pages = 42,
        updated = 1_700_000_000_000,
        scrollOffset = 7,
        read = read,
    )

    @Test
    fun `round trip keeps progress and read ranges`() {
        ProgressStore.writeTo(file(), mapOf("book-1" to entry(3, listOf(0..99, 500..599))))
        val loaded = ProgressStore.loadFrom(file())
        assertEquals(1, loaded.size)
        val p = loaded.getValue("book-1")
        assertEquals(3, p.chapter)
        assertEquals(123, p.addr)
        assertEquals(42, p.pages)
        assertEquals(7, p.scrollOffset)
        assertEquals(listOf(0..99, 500..599), p.read)
    }

    @Test
    fun `corrupt file is quarantined instead of being lost`() {
        file().writeText("{ this is not json")
        val loaded = ProgressStore.loadFrom(file())
        assertTrue("uszkodzony plik => pusta mapa", loaded.isEmpty())
        assertFalse("oryginał nie może zostać na miejscu", file().exists())
        val saved = tmp.root.listFiles()!!.filter { it.name.startsWith("progress.json.corrupt-") }
        assertEquals("uszkodzony plik ma być odłożony, nie skasowany", 1, saved.size)
    }

    @Test
    fun `corrupt file is restored from bak instead of losing everything`() {
        ProgressStore.writeTo(file(), mapOf("book-1" to entry(2, listOf(0..50))))
        // drugi zapis zostawia poprzednią wersję w .bak
        ProgressStore.writeTo(file(), mapOf("book-1" to entry(3, listOf(0..99))))
        file().writeText("{broken")

        val loaded = ProgressStore.loadFrom(file())
        assertEquals("postęp wraca z .bak", setOf("book-1"), loaded.keys)
        assertEquals(2, loaded.getValue("book-1").chapter)
        assertEquals(listOf(0..50), loaded.getValue("book-1").read)
        assertEquals("plik główny naprawiony", 2, ProgressStore.loadFrom(file()).getValue("book-1").chapter)
        assertEquals(
            "nic nie poszło do kwarantanny",
            0,
            tmp.root.listFiles()!!.count { it.name.contains(".corrupt-") },
        )
    }

    @Test
    fun `missing file is not an error`() {
        assertTrue(ProgressStore.loadFrom(File(tmp.root, "nothing-here.json")).isEmpty())
    }

    @Test
    fun `write after quarantine does not resurrect old data`() {
        file().writeText("garbage")
        ProgressStore.loadFrom(file()) // kwarantanna
        ProgressStore.writeTo(file(), mapOf("book-2" to entry(1, listOf(0..10))))
        val loaded = ProgressStore.loadFrom(file())
        assertEquals(setOf("book-2"), loaded.keys)
    }
}
