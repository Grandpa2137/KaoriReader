package app.kaori.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Serce spoiler guardu: jeśli te testy padną, Kaori zacznie znać nieprzeczytane strony.
 * Czysta logika na listach — bez Androida, bez sieci.
 */
class ReadRangesTest {

    @Test
    fun `add keeps ranges sorted and merged`() {
        var r = emptyList<IntRange>()
        r = ReadRanges.add(r, 100, 200)
        r = ReadRanges.add(r, 0, 50)
        r = ReadRanges.add(r, 300, 320)
        assertEquals(listOf(0..49, 100..199, 300..319), r.map { it.first..it.last })
    }

    @Test
    fun `add merges overlapping ranges`() {
        var r = ReadRanges.add(emptyList(), 0, 100)
        r = ReadRanges.add(r, 50, 150)
        assertEquals(1, r.size)
        assertEquals(0, r[0].first)
        assertEquals(149, r[0].last)
    }

    @Test
    fun `add merges adjacent ranges`() {
        var r = ReadRanges.add(emptyList(), 0, 100)
        r = ReadRanges.add(r, 100, 200)
        assertEquals(1, r.size)
        assertEquals(199, r[0].last)
    }

    @Test
    fun `add ignores empty range`() {
        val r = ReadRanges.add(emptyList(), 50, 50)
        assertTrue(r.isEmpty())
    }

    @Test
    fun `gap stays unread`() {
        var r = ReadRanges.add(emptyList(), 0, 100)
        r = ReadRanges.add(r, 500, 600)
        assertTrue(ReadRanges.isRead(r, 50))
        assertFalse(ReadRanges.isRead(r, 300))
        assertTrue(ReadRanges.isRead(r, 550))
    }

    @Test
    fun `union merges two lists`() {
        val a = ReadRanges.add(ReadRanges.add(emptyList(), 0, 100), 400, 500)
        val b = ReadRanges.add(ReadRanges.add(emptyList(), 90, 120), 460, 480)
        val u = ReadRanges.union(a, b)
        assertEquals(listOf(0..119, 400..499), u.map { it.first..it.last })
    }

    @Test
    fun `prune keeps the list bounded`() {
        var r = emptyList<IntRange>()
        // 300 kawałków z dziurami — powyżej limitu 400 nie dojdziemy, ale sprawdzamy stabilność
        for (i in 0 until 300) {
            r = ReadRanges.add(r, i * 100, i * 100 + 40)
        }
        r = ReadRanges.add(r, 1_000_000, 1_000_100)
        assertTrue("zakresy muszą zostać posortowane", r.zipWithNext().all { it.first.last < it.second.first })
        assertEquals(301, r.size)
    }

    @Test
    fun `textOf returns only read text`() {
        val book = Book(
            id = "b",
            title = "t",
            author = "a",
            chapters = listOf(
                Chapter("c1", "AAAA"),
                Chapter("c2", "BBBB"),
            ),
        )
        // cała książka: 0..7; zakres 0..5 to cały pierwszy rozdział i dwa znaki drugiego.
        // textOf dokłada '\n' po każdym kawałku, więc porównujemy bez białych znaków.
        val r = ReadRanges.add(emptyList(), 0, 6)
        assertEquals("AAAABB", ReadRanges.textOf(book, r).replace("\n", ""))
    }

    @Test
    fun `textOf skips gaps between chapters`() {
        val book = Book(
            id = "b",
            title = "t",
            author = "a",
            chapters = listOf(
                Chapter("c1", "12345"),
                Chapter("c2", "67890"),
            ),
        )
        var r = ReadRanges.add(emptyList(), 0, 2)
        r = ReadRanges.add(r, 8, 10)
        val out = ReadRanges.textOf(book, r)
        assertEquals("1290", out.replace("\n", ""))
    }
}
