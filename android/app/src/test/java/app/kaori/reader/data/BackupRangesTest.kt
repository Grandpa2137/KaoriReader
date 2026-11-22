package app.kaori.reader.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Zakresy z pliku kopii zapasowej wchodzą prosto do spoiler guarda, więc
 * uszkodzony albo złośliwy plik nie może ich rozszerzyć ani wywrócić importu.
 */
class BackupRangesTest {

    @Test
    fun `valid pairs become ranges`() {
        val arr = JSONArray()
            .put(JSONArray().put(0).put(99))
            .put(JSONArray().put(500).put(599))
        assertEquals(listOf(0..99, 500..599), Backup.readRanges(arr))
    }

    @Test
    fun `missing array means nothing read`() {
        assertTrue(Backup.readRanges(null).isEmpty())
    }

    @Test
    fun `reversed pairs are dropped`() {
        val arr = JSONArray().put(JSONArray().put(500).put(100))
        assertTrue(Backup.readRanges(arr).isEmpty())
    }

    @Test
    fun `negative start is dropped`() {
        val arr = JSONArray().put(JSONArray().put(-50).put(100))
        assertTrue(Backup.readRanges(arr).isEmpty())
    }

    @Test
    fun `garbage entries are skipped, not fatal`() {
        val arr = JSONArray()
            .put("not an array")
            .put(JSONArray())              // pusta para
            .put(JSONArray().put(10).put(20))
        assertEquals(listOf(10..20), Backup.readRanges(arr))
    }

    @Test
    fun `touching ranges are merged`() {
        val arr = JSONArray()
            .put(JSONArray().put(0).put(99))
            .put(JSONArray().put(100).put(199))
        assertEquals(listOf(0..199), Backup.readRanges(arr))
    }

    @Test
    fun `single character range survives`() {
        val arr = JSONArray().put(JSONArray().put(42).put(42))
        assertEquals(listOf(42..42), Backup.readRanges(arr))
    }
}
