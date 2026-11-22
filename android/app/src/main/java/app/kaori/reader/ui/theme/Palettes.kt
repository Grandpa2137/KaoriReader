package app.kaori.reader.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palety Kaori Reader — źródło: D:\KaoriReader\shared\themes.json
 * (kolory wyciągnięte z D:\Kaori\Kaori Design.png i wallpaperu z sakurą)
 */
data class KaoriPalette(
    val id: String,
    val name: String,
    // czytnik
    val readerBg: Color,
    val readerText: Color,
    val readerSurface: Color,
    // interfejs
    val uiBg: Color,
    val uiSurface: Color,
    val uiSurfaceRaised: Color,
    val text: Color,
    val textMuted: Color,
    val textDim: Color,
    val accent: Color,
    val accentSecondary: Color,
    // gradient wiadomości użytkownika
    val gradFrom: Color,
    val gradTo: Color,
    val bubbleKaori: Color,
    val border: Color,
)

val PaletteKaori = KaoriPalette(
    id = "kaori",
    name = "Kaori",
    readerBg = Color(0xFF2A2340),
    readerText = Color(0xFFEDE8F6),
    readerSurface = Color(0xFF322A4C),
    uiBg = Color(0xFF241E36),
    uiSurface = Color(0xFF2E2645),
    uiSurfaceRaised = Color(0xFF3A3157),
    text = Color(0xFFF1ECF8),
    textMuted = Color(0xFFB6ACD0),
    textDim = Color(0xFF8D82A8),
    accent = Color(0xFF9D8AE0),
    accentSecondary = Color(0xFFF0A8C8),
    gradFrom = Color(0xFF7A68C8),
    gradTo = Color(0xFFC07AA8),
    bubbleKaori = Color(0xFF3A3157),
    border = Color(0xFF4A3F6B),
)

val PaletteNoc = KaoriPalette(
    id = "noc",
    name = "Night",
    readerBg = Color(0xFF202838),
    readerText = Color(0xFFE2E8F4),
    readerSurface = Color(0xFF283146),
    uiBg = Color(0xFF1C2433),
    uiSurface = Color(0xFF283146),
    uiSurfaceRaised = Color(0xFF333E57),
    text = Color(0xFFE8EEF8),
    textMuted = Color(0xFFA6B2CB),
    textDim = Color(0xFF7C89A4),
    accent = Color(0xFF7F96E0),
    accentSecondary = Color(0xFFA8C4E8),
    gradFrom = Color(0xFF4A63A8),
    gradTo = Color(0xFF6E86C0),
    bubbleKaori = Color(0xFF333E57),
    border = Color(0xFF43506D),
)

// --- jasne motywy: czytanie w dzień, bez czerni w tle ---

val PaletteSepia = KaoriPalette(
    id = "sepia",
    name = "Sepia",
    readerBg = Color(0xFFF6EEDF),
    readerText = Color(0xFF3B3227),
    readerSurface = Color(0xFFEFE3CE),
    uiBg = Color(0xFFFBF5EA),
    uiSurface = Color(0xFFF2E8D8),
    uiSurfaceRaised = Color(0xFFE8DAC3),
    text = Color(0xFF3B3227),
    textMuted = Color(0xFF6E6153),
    textDim = Color(0xFF99897A),
    accent = Color(0xFFA9704B),
    accentSecondary = Color(0xFFC08A68),
    gradFrom = Color(0xFFD9A87C),
    gradTo = Color(0xFFB98457),
    bubbleKaori = Color(0xFFEFE2CE),
    border = Color(0xFFE0D2BB),
)

val PaletteHanami = KaoriPalette(
    id = "hanami",
    name = "Hanami",
    readerBg = Color(0xFFFDF3F6),
    readerText = Color(0xFF463039),
    readerSurface = Color(0xFFF6E6EC),
    uiBg = Color(0xFFFBF0F4),
    uiSurface = Color(0xFFF6E6EC),
    uiSurfaceRaised = Color(0xFFEFD9E3),
    text = Color(0xFF463039),
    textMuted = Color(0xFF79606A),
    textDim = Color(0xFFA08A94),
    accent = Color(0xFFC05A85),
    accentSecondary = Color(0xFF9A7AD0),
    gradFrom = Color(0xFFD97BA4),
    gradTo = Color(0xFF9B7AD0),
    bubbleKaori = Color(0xFFF4E1E9),
    border = Color(0xFFEBD3DE),
)

val PaletteMgla = KaoriPalette(
    id = "mgla",
    name = "Mist",
    readerBg = Color(0xFFF4F2F9),
    readerText = Color(0xFF363045),
    readerSurface = Color(0xFFEBE6F4),
    uiBg = Color(0xFFF7F5FC),
    uiSurface = Color(0xFFEDE9F5),
    uiSurfaceRaised = Color(0xFFE2DCF0),
    text = Color(0xFF363045),
    textMuted = Color(0xFF645C7A),
    textDim = Color(0xFF8F87A3),
    accent = Color(0xFF6F5FC0),
    accentSecondary = Color(0xFFB06A9E),
    gradFrom = Color(0xFF8A7AD0),
    gradTo = Color(0xFFC08AAE),
    bubbleKaori = Color(0xFFE9E3F3),
    border = Color(0xFFDCD5EA),
)

val AllPalettes = listOf(PaletteKaori, PaletteNoc, PaletteSepia, PaletteHanami, PaletteMgla)
