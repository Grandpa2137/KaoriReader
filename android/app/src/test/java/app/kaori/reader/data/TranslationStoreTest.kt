package app.kaori.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Cache tłumaczeń: klucz = tekst + język + książka, brak wyciekania między książkami. */
class TranslationStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `put then get returns the same text`() {
        TranslationStore.putInto(tmp.root, "book-1", "Polish", "Chapter one text", "Rozdział pierwszy")
        assertEquals(
            "Rozdział pierwszy",
            TranslationStore.getFrom(tmp.root, "book-1", "Polish", "Chapter one text"),
        )
    }

    @Test
    fun `missing entry returns null`() {
        assertNull(TranslationStore.getFrom(tmp.root, "book-1", "Polish", "nope"))
    }

    @Test
    fun `different language is a different entry`() {
        TranslationStore.putInto(tmp.root, "book-1", "Polish", "hello", "cześć")
        TranslationStore.putInto(tmp.root, "book-1", "English", "hello", "hello (en)")
        assertEquals("cześć", TranslationStore.getFrom(tmp.root, "book-1", "Polish", "hello"))
        assertEquals("hello (en)", TranslationStore.getFrom(tmp.root, "book-1", "English", "hello"))
    }

    @Test
    fun `books do not share translations`() {
        TranslationStore.putInto(tmp.root, "book-1", "Polish", "same text", "z książki 1")
        assertNull(TranslationStore.getFrom(tmp.root, "book-2", "Polish", "same text"))
    }

    @Test
    fun `edited source text does not hit the old entry`() {
        TranslationStore.putInto(tmp.root, "book-1", "Polish", "original", "przekład")
        assertNull(
            "zmieniony tekst źródłowy musi dać nowy klucz",
            TranslationStore.getFrom(tmp.root, "book-1", "Polish", "original (edited)"),
        )
    }

    @Test
    fun `blank input is ignored`() {
        TranslationStore.putInto(tmp.root, "book-1", "Polish", "", "coś")
        TranslationStore.putInto(tmp.root, "book-1", "Polish", "text", "")
        assertEquals(0L, TranslationStore.sizeBytesIn(tmp.root))
    }

    @Test
    fun `size grows with entries`() {
        TranslationStore.putInto(tmp.root, "book-1", "Polish", "a", "b")
        assertTrue(TranslationStore.sizeBytesIn(tmp.root) > 0)
        val first = TranslationStore.sizeBytesIn(tmp.root)
        TranslationStore.putInto(tmp.root, "book-1", "Polish", "c", "d")
        assertNotEquals(first, TranslationStore.sizeBytesIn(tmp.root))
    }
}
