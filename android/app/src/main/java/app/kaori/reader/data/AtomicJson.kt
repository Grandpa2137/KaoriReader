package app.kaori.reader.data

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
 * Przy odczycie najpierw próbujemy `plik.bak` — dopiero gdy i on jest zły, plik idzie
 * do kwarantanny.
 */
object AtomicJson {

    private const val TAG = "AtomicJson"

    fun write(file: File, text: String) {
        val dir = file.parentFile ?: return
        if (!dir.exists()) dir.mkdirs()
        val tmp = File(dir, file.name + ".tmp")
        val bytes = text.toByteArray(Charsets.UTF_8)

        // treść musi być na dysku ZANIM podmienimy nazwę — sam rename nie wystarczy
        // po nagłym zaniku zasilania
        runCatching { flushTo(tmp, bytes) }.onFailure {
            Log.e(TAG, "nie udało się zapisać ${tmp.name}", it)
            return
        }

        // kopia poprzedniej wersji — tylko jeśli plik istnieje i jest niepusty
        if (file.exists() && file.length() > 0) {
            runCatching { file.copyTo(File(dir, file.name + ".bak"), overwrite = true) }
        }

        if (moveInto(tmp, file)) return

        // ostatnia deska ratunku: kopiujemy treść do pliku docelowego (mniej bezpiecznie,
        // ale lepsze niż zostawienie starej wersji)
        runCatching {
            flushTo(file, bytes)
            tmp.delete()
        }.onFailure { Log.e(TAG, "zapis ${file.name} nie udał się", it) }
    }

    private fun flushTo(target: File, bytes: ByteArray) {
        FileOutputStream(target).use { out ->
            out.write(bytes)
            out.flush()
            runCatching { out.fd.sync() }
        }
    }

    /**
     * Rename jest atomowy na jednym systemie plików; NIO potrafi dodatkowo podmienić
     * istniejący plik, więc próbujemy jego, a `renameTo` zostaje jako fallback.
     */
    private fun moveInto(from: File, to: File): Boolean {
        val ok = runCatching {
            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            true
        }.getOrElse { false }
        if (ok) return true
        return runCatching { from.renameTo(to) }.getOrDefault(false)
    }

    /** Nazwa pliku z poprzednią, dobrą wersją. */
    private fun backupOf(file: File) = File(file.parentFile, file.name + ".bak")

    /**
     * Czyta plik i parsuje go podanym `parse`; przy uszkodzeniu sięga po `.bak`.
     * Zwraca `null`, gdy nie ma czego ratować — wtedy plik główny idzie do kwarantanny.
     *
     * Dzięki temu po awarii użytkownik widzi stan z poprzedniego zapisu, a nie pustą
     * bibliotekę z dobrą kopią leżącą obok.
     */
    fun <T> read(file: File, parse: (String) -> T): T? {
        if (!file.exists()) return null
        runCatching { parse(file.readText()) }.getOrNull()?.let { return it }

        val bak = backupOf(file)
        if (bak.exists()) {
            val restored = runCatching { parse(bak.readText()) }.getOrNull()
            if (restored != null) {
                Log.w(TAG, "${file.name} uszkodzony — wczytano ${bak.name}")
                // od razu naprawiamy plik główny, żeby kolejne odczyty nie sięgały do kopii
                runCatching { write(file, bak.readText()) }
                return restored
            }
        }

        quarantine(file)
        return null
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
