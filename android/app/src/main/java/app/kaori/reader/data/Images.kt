package app.kaori.reader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/** Zdjęcia do czatu: zmniejszanie i base64 dla API. */
object Images {

    private const val MAX_PX = 1024
    // Zdjęcie z aparatu ma kilka megabajtów; limit chroni przed OOM na ogromnym pliku.
    private const val MAX_SOURCE_BYTES = 24 * 1024 * 1024

    fun save(context: Context, uri: Uri, thread: String): File? = runCatching {
        val raw = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream(64 * 1024)
            val chunk = ByteArray(16 * 1024)
            var total = 0
            while (total < MAX_SOURCE_BYTES) {
                val n = input.read(chunk, 0, minOf(chunk.size, MAX_SOURCE_BYTES - total))
                if (n <= 0) break
                out.write(chunk, 0, n)
                total += n
            }
            out.toByteArray()
        } ?: return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > MAX_PX * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, opts) ?: return null

        // dodatkowo skalujemy, żeby nie wysyłać 4000 px do modelu
        val scale = MAX_PX.toFloat() / maxOf(bmp.width, bmp.height)
        val out = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bmp
        }

        val dir = File(context.filesDir, "chat_images").apply { mkdirs() }
        val file = File(dir, "${thread.replace(Regex("[^A-Za-z0-9_-]"), "_")}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        if (out !== bmp) out.recycle()
        bmp.recycle()
        file
    }.getOrNull()

    fun toBase64(file: File, maxPx: Int = 0): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        if (maxPx > 0 && bounds.outWidth > 0) {
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxPx * 2) sample *= 2
        }
        val bmp = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        // Ilustracje z książek potrafią mieć 1500 px — do modelu więcej nie trzeba.
        val out = if (maxPx > 0) {
            val s = maxPx.toFloat() / maxOf(bmp.width, bmp.height)
            if (s < 1f) Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * s).toInt().coerceAtLeast(1),
                (bmp.height * s).toInt().coerceAtLeast(1),
                true,
            ) else bmp
        } else bmp
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 88, bos)
        if (out !== bmp) out.recycle()
        bmp.recycle()
        Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}
