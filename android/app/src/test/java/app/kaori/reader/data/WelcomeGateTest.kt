package app.kaori.reader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Karta „Before you start" ma trafić dokładnie do jednej osoby: tej, która właśnie
 * zainstalowała apkę i nie ma jeszcze klucza. Każdy inny przypadek to albo wykład
 * dla kogoś, kto już wie, albo brak pomocy dla nowego.
 */
class WelcomeGateTest {

    @Test
    fun firstInstallSeesTheCard() {
        assertTrue(shouldShowWelcome(seen = false, apiKey = ""))
    }

    @Test
    fun updatingWithAKeyDoesNotSeeIt() {
        // Właściciel tego testu: 0.2.12 -> 0.2.13. Ma klucz, nie chce czytać o kluczach.
        assertFalse(shouldShowWelcome(seen = false, apiKey = "sk-live-key"))
    }

    @Test
    fun dismissIsRemembered() {
        assertFalse(shouldShowWelcome(seen = true, apiKey = ""))
    }

    @Test
    fun dismissedAndKeyedStaysQuiet() {
        assertFalse(shouldShowWelcome(seen = true, apiKey = "sk-live-key"))
    }

    @Test
    fun whitespaceOnlyKeyCountsAsMissing() {
        // Klucz ze spacjami to brak klucza — apka i tak go nie użyje, więc karta się należy.
        assertTrue(shouldShowWelcome(seen = false, apiKey = "   "))
    }
}
