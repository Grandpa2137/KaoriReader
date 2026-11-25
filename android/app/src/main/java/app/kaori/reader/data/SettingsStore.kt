package app.kaori.reader.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "kaori_settings")

/** Settings trwałe: motyw, font, rozmiar, tryb przewijania, klucze API. */
data class AppSettings(
    val themeId: String = "kaori",
    val fontId: String = "literata",
    val fontSize: Float = 15f,
    val scrollMode: Boolean = false,
    val apiKey: String = "",
    /** Kaori zna tylko przeczytane kawałki książki. */
    val spoilerGuard: Boolean = true,
    /** Adres API dostawcy (OpenAI-compatible) i nazwa modelu. */
    val baseUrl: String = "https://api.deepseek.com",
    val model: String = "deepseek-chat",
    /** Każda odpowiedź w roli, bez wzmianek o AI i meta-komentarza. */
    val strictRole: Boolean = true,
    /** Czy model ma się zastanowić przed odpowiedzią (działa tam, gdzie da się włączyć). */
    val thinking: Boolean = true,
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme_id")
        val font = stringPreferencesKey("font_id")
        val fontSize = floatPreferencesKey("font_size")
        val scroll = androidx.datastore.preferences.core.booleanPreferencesKey("scroll_mode")
        val apiKey = stringPreferencesKey("api_key")
        val spoiler = androidx.datastore.preferences.core.booleanPreferencesKey("spoiler_guard")
        val baseUrl = stringPreferencesKey("llm_base_url")
        val model = stringPreferencesKey("llm_model")
        val strict = androidx.datastore.preferences.core.booleanPreferencesKey("strict_role")
        val thinking = androidx.datastore.preferences.core.booleanPreferencesKey("llm_thinking")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeId = p[Keys.theme] ?: "kaori",
            fontId = p[Keys.font] ?: "literata",
            fontSize = p[Keys.fontSize] ?: 15f,
            scrollMode = p[Keys.scroll] ?: false,
            apiKey = SecureKey.decrypt(p[Keys.apiKey] ?: ""),
            spoilerGuard = p[Keys.spoiler] ?: true,
            baseUrl = p[Keys.baseUrl] ?: "https://api.deepseek.com",
            model = p[Keys.model] ?: "deepseek-chat",
            strictRole = p[Keys.strict] ?: true,
            thinking = p[Keys.thinking] ?: true,
        )
    }

    suspend fun setTheme(id: String) = put { it[Keys.theme] = id }
    suspend fun setFont(id: String) = put { it[Keys.font] = id }
    suspend fun setFontSize(v: Float) = put { it[Keys.fontSize] = v }
    suspend fun setScrollMode(v: Boolean) = put { it[Keys.scroll] = v }
    /** @return false, jeśli Keystore odmówił — wtedy w ustawieniach ma pojawić się błąd. */
    suspend fun setApiKey(v: String): Boolean {
        val plain = v.trim()
        if (plain.isEmpty()) {
            put { it[Keys.apiKey] = "" }
            return true
        }
        val enc = SecureKey.encryptOrNull(plain) ?: return false
        put { it[Keys.apiKey] = enc }
        return true
    }
    suspend fun setSpoilerGuard(v: Boolean) = put { it[Keys.spoiler] = v }
    suspend fun setBaseUrl(v: String) = put { it[Keys.baseUrl] = v.trim() }
    suspend fun setModel(v: String) = put { it[Keys.model] = v.trim() }
    suspend fun setStrictRole(v: Boolean) = put { it[Keys.strict] = v }
    suspend fun setThinking(v: Boolean) = put { it[Keys.thinking] = v }

    private suspend fun put(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    companion object {
        fun themeFor(id: String) =
            app.kaori.reader.ui.theme.AllPalettes.firstOrNull { it.id == id }
                ?: app.kaori.reader.ui.theme.PaletteKaori
    }
}
