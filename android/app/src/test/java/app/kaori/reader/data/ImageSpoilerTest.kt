package app.kaori.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ilustracje muszą podlegać temu samemu blokowi co tekst. To był jedyny
 * prawdziwy wyciek spoilerów: obrazek z nieprzeczytanego rozdziału szedł do modelu,
 * bo liczyło się „wszystko do bieżącego rozdziału", a nie przeczytane zakresy.
 */
class ImageSpoilerTest {

    private fun book(): Book {
        // Rozdział 1: 100 znaków, obrazek pod offsetem 50.
        // Rozdział 2: 100 znaków (offset globalny 100..199), obrazek pod offsetem 20 → 120.
        // Rozdział 3: obrazek pod offsetem 0 → 200.
        val ch1 = Chapter(
            title = "One",
            text = "a".repeat(100),
            images = listOf(ChapterImage(50, "/img/ch1-1.jpg")),
        )
        val ch2 = Chapter(
            title = "Two",
            text = "b".repeat(100),
            images = listOf(ChapterImage(20, "/img/ch2-1.jpg")),
        )
        val ch3 = Chapter(
            title = "Three",
            text = "c".repeat(100),
            images = listOf(ChapterImage(0, "/img/ch3-1.jpg")),
        )
        return Book("b1", "Test", "me", listOf(ch1, ch2, ch3))
    }

    @Test
    fun `image from an unread chapter never reaches the prompt`() {
        // Przeczytane tylko 0..99 (rozdział 1) — obrazki z rozdziałów 2 i 3 odpadają.
        val images = ReadRanges.imagesIn(book(), listOf(0..99), max = 5)
        assertEquals(listOf("/img/ch1-1.jpg"), images)
    }

    @Test
    fun `image past the reading position inside a chapter is dropped`() {
        // Przeczytane 0..60: obrazek z rozdziału 1 (offset 50) jest, reszta nie.
        assertEquals(listOf("/img/ch1-1.jpg"), ReadRanges.imagesIn(book(), listOf(0..60), max = 5))
        // Przeczytane tylko 0..40: obrazek pod 50 leży już za oczami czytelnika.
        assertTrue(ReadRanges.imagesIn(book(), listOf(0..40), max = 5).isEmpty())
    }

    @Test
    fun `reading further brings later images in`() {
        val images = ReadRanges.imagesIn(book(), listOf(0..199), max = 5)
        assertEquals(listOf("/img/ch1-1.jpg", "/img/ch2-1.jpg"), images)
    }

    @Test
    fun `gap in the middle keeps its images out`() {
        // Przeczytane 0..99 i 200..299 (rozdział 3), rozdział 2 pominięty.
        val images = ReadRanges.imagesIn(book(), listOf(0..99, 200..299), max = 5)
        assertEquals(listOf("/img/ch1-1.jpg", "/img/ch3-1.jpg"), images)
    }

    @Test
    fun `max keeps the last ones`() {
        assertEquals(
            listOf("/img/ch3-1.jpg"),
            ReadRanges.imagesIn(book(), listOf(0..299), max = 1),
        )
        assertTrue(ReadRanges.imagesIn(book(), emptyList(), max = 5).isEmpty())
    }

    @Test
    fun `guard off means the whole book, like the text`() {
        assertEquals(
            listOf("/img/ch1-1.jpg", "/img/ch2-1.jpg", "/img/ch3-1.jpg"),
            ReadRanges.imagesVisible(book(), ranges = emptyList(), max = 5, guard = false, legacyChapter = 0),
        )
    }

    @Test
    fun `no ranges yet falls back to the current chapter, exactly like visibleText`() {
        // Stara książka: postęp jest, zakresów nie ma. Tekst idzie do bieżącego rozdziału,
        // więc obrazki też — ani więcej, ani mniej.
        assertEquals(
            listOf("/img/ch1-1.jpg"),
            ReadRanges.imagesVisible(book(), ranges = emptyList(), max = 5, guard = true, legacyChapter = 0),
        )
        assertEquals(
            listOf("/img/ch1-1.jpg", "/img/ch2-1.jpg"),
            ReadRanges.imagesVisible(book(), ranges = emptyList(), max = 5, guard = true, legacyChapter = 1),
        )
        assertEquals(
            listOf("/img/ch1-1.jpg", "/img/ch2-1.jpg", "/img/ch3-1.jpg"),
            ReadRanges.imagesVisible(book(), ranges = emptyList(), max = 5, guard = true, legacyChapter = 99),
        )
    }

    @Test
    fun `ranges win over the chapter fallback`() {
        // Zakresy są, więc decydują one — nawet gdy bieżący rozdział jest dalej.
        assertEquals(
            listOf("/img/ch1-1.jpg"),
            ReadRanges.imagesVisible(book(), ranges = listOf(0..99), max = 5, guard = true, legacyChapter = 2),
        )
    }
}
