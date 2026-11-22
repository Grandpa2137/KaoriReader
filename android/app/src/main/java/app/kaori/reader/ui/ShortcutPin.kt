package app.kaori.reader.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.kaori.reader.MainActivity
import app.kaori.reader.R

/**
 * Przypina skrót do pulpitu telefonu.
 *
 * Od Androida 8 nie da się tego zrobić po cichu — system pokazuje wtedy
 * okienko „Dodać skrót?”. Typowy launcher (Pixel/Moto) to obsługuje.
 */
fun pinAppShortcut(context: Context): Boolean {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false

    // Kafelek dostaje monogram „K”, a nie twarz: launcher dorysowuje w rogu
    // miniaturę ikony aplikacji, więc dwie twarze wyglądały jak błąd.
    val shortcut = ShortcutInfoCompat.Builder(context, "kaori_reader_v5")
        .setShortLabel("KaoriReader")
        .setLongLabel("KaoriReader — czytnik light novel")
        .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_shortcut))
        .setIntent(Intent(context, MainActivity::class.java).apply { action = Intent.ACTION_MAIN })
        .build()
    return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
}
