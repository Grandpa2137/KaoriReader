package app.kaori.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Atomowy zapis to jedyna rzecz, która stoi między użytkownikiem a utratą całej biblioteki.
 * Te testy sprawdzają dokładnie ten scenariusz, który opisał recenzent.
 */
class AtomicJsonTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `write creates file and parent dirs`() {
        val f = File(tmp.root, "nested/dir/data.json")
        AtomicJson.write(f, """{"a":1}""")
        assertTrue(f.exists())
        assertEquals("""{"a":1}""", f.readText())
    }

    @Test
    fun `previous version is kept as bak`() {
        val f = File(tmp.root, "progress.json")
        AtomicJson.write(f, "first")
        AtomicJson.write(f, "second")
        assertEquals("second", f.readText())
        val bak = File(tmp.root, "progress.json.bak")
        assertTrue("kopia poprzedniej wersji musi istnieć", bak.exists())
        assertEquals("first", bak.readText())
    }

    @Test
    fun `no temp file left behind`() {
        val f = File(tmp.root, "data.json")
        AtomicJson.write(f, "x")
        assertFalse(File(tmp.root, "data.json.tmp").exists())
    }

    @Test
    fun `quarantine moves the file aside and keeps content`() {
        val f = File(tmp.root, "progress.json")
        f.writeText("{broken")
        AtomicJson.quarantine(f)
        assertFalse("oryginał nie może zostać na miejscu", f.exists())
        val leftover = tmp.root.listFiles()!!.filter { it.name.startsWith("progress.json.corrupt-") }
        assertEquals(1, leftover.size)
        assertEquals("{broken", leftover.first().readText())
    }

    @Test
    fun `quarantine on missing file does nothing`() {
        val f = File(tmp.root, "nope.json")
        AtomicJson.quarantine(f)
        assertTrue(tmp.root.listFiles()!!.isEmpty())
    }
}
