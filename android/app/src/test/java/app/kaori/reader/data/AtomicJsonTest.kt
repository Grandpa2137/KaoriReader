package app.kaori.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `read returns parsed value`() {
        val f = File(tmp.root, "data.json")
        AtomicJson.write(f, """{"v":1}""")
        assertEquals(1, AtomicJson.read(f) { v(it) })
    }

    @Test
    fun `read falls back to bak when the file is broken`() {
        val f = File(tmp.root, "progress.json")
        AtomicJson.write(f, """{"v":1}""")   // pierwszy zapis
        AtomicJson.write(f, """{"v":2}""")   // .bak = wersja 1
        f.writeText("{broken")              // udajemy przerwany zapis
        assertEquals(1, AtomicJson.read(f) { v(it) })
    }

    @Test
    fun `read repairs the main file from bak`() {
        val f = File(tmp.root, "progress.json")
        AtomicJson.write(f, """{"v":1}""")
        AtomicJson.write(f, """{"v":2}""")
        f.writeText("{broken")
        AtomicJson.read(f) { v(it) }
        assertEquals("plik główny naprawiony treścią .bak", 1, AtomicJson.read(f) { v(it) })
        assertEquals("""{"v":1}""", f.readText())
    }

    @Test
    fun `read quarantines when main and bak are both broken`() {
        val f = File(tmp.root, "progress.json")
        f.writeText("{broken")
        File(tmp.root, "progress.json.bak").writeText("also broken")
        assertNull(AtomicJson.read(f) { v(it) })
        assertFalse(f.exists())
        assertEquals(
            1,
            tmp.root.listFiles()!!.count { it.name.startsWith("progress.json.corrupt-") },
        )
    }

    @Test
    fun `read of missing file is null`() {
        assertNull(AtomicJson.read(File(tmp.root, "absent.json")) { v(it) })
    }

    /** Parser, który — jak prawdziwy JSONObject — rzuca na uszkodzonej treści. */
    private fun v(text: String): Int = org.json.JSONObject(text).getInt("v")
}
