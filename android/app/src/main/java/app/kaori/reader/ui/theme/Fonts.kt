package app.kaori.reader.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.kaori.reader.R

/**
 * Fonty do czytania. All z Google Fonts (licencja OFL / Apache).
 * Pliki leżą w res/font/.
 */
data class ReaderFont(
    val id: String,
    val name: String,
    val family: FontFamily,
    val isSerif: Boolean,
)

private fun fam(res: Int) = FontFamily(Font(res))

val AllFonts: List<ReaderFont> = listOf(
    ReaderFont("literata", "Literata", fam(R.font.literata), true),
    ReaderFont("ebgaramond", "EB Garamond", fam(R.font.ebgaramond), true),
    ReaderFont("sourceserif", "Source Serif", fam(R.font.sourceserif), true),
    ReaderFont("lora", "Lora", fam(R.font.lora), true),
    ReaderFont("crimsonpro", "Crimson Pro", fam(R.font.crimsonpro), true),
    ReaderFont("crimsontext", "Crimson Text", fam(R.font.crimsontext), true),
    ReaderFont("alegreya", "Alegreya", fam(R.font.alegreya), true),
    ReaderFont("cormorant", "Cormorant", fam(R.font.cormorant), true),
    ReaderFont("bitter", "Bitter", fam(R.font.bitter), true),
    ReaderFont("zillaslab", "Zilla Slab", fam(R.font.zillaslab), true),
    ReaderFont("ibmplexserif", "IBM Plex Serif", fam(R.font.ibmplexserif), true),
    ReaderFont("notoserif", "Noto Serif", fam(R.font.notoserif), true),
    ReaderFont("playfair", "Playfair Display", fam(R.font.playfair), true),
    ReaderFont("atkinson", "Atkinson Hyperlegible", fam(R.font.atkinson), false),
    ReaderFont("andika", "Andika", fam(R.font.andika), false),
    ReaderFont("inter", "Inter", fam(R.font.inter), false),
    ReaderFont("karla", "Karla", fam(R.font.karla), false),
)

val DefaultFont: ReaderFont = AllFonts.first()

fun fontById(id: String?): ReaderFont = AllFonts.firstOrNull { it.id == id } ?: DefaultFont

// Rozmiary tekstu czytnika (sp)
val ReaderFontSizes = listOf(13f, 14f, 15f, 16f, 17f, 18f, 20f, 22f)
