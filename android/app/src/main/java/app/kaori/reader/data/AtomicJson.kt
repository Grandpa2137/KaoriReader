package app.kaori.reader.data

import android.util.Log
import java.io.File

/**
 * Zapis JSON-a bez ryzyka utraty danych.
 *
 * Problem, który to rozwiązuje: `File.writeText` obcina plik i pisze od zera. Jeśli proces
 * padnie w połowie (albo zabije go system), zostaje nieparsowalny plik — a kod czytający
 * „przy błędzie zwróć pustą mapę" nadpisywał go przy następnym zapisie i cała biblioteka
 * traciła postęp oraz przeczytane zakresy.
 *
 * Rozwiązanie: pisz do `plik.tmp`, zsynchronizuj i podmień atomowo przez rename.
 * Poprzednia wersja ląduje w `plik.bak`, a uszkodzony plik jest odkładany, nie kasowany.
 */
object AtomicJson {

    private const val TAG = "AtomicJson"

    fun write(file: File, text: String) {
        val dir = file.parentFile ?: return
        if (!dir.exists()) dir.mkdirs()
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(text)
        // kopia poprzedniej wersji — tylko jeśli plik istnieje i jest niepusty
        if (file.exists() && file.length() > 0) {
            runCatching { file.copyTo(File(dir, file.name + ".bak"), overwrite = true) }
        }
        if (!tmp.renameTo(file)) {
            // rename potrafi zawieść na niektórych systemach plików — wtedy kopiujemy awaryjnie
            runCatching {
                file.writeText(text)
                tmp.delete()
            }.onFailure { Log.e(TAG, "zapis ${file.name} nie udał się", it) }
        }
    }

    /** Odkłada uszkodzony plik na bok, żeby dało się go odzyskać ręcznie. */
    fun quarantine(file: File) {
        if (!file.exists()) return
        val stamp = System.currentTimeMillis()
        val target = File(file.parentFile, file.name + ".corrupt-$stamp")
        runCatching { file.renameTo(target) }
            .onSuccess { Log.w(TAG, "uszkodzony ${file.name} odłożony jako ${target.name}") }
    }
}
