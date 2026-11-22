package app.kaori.reader.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Szyfrowanie kluczy API kluczem z Android Keystore.
 *
 * Klucz nigdy nie opuszcza urządzenia (nie da się go wyeksportować), a w
 * DataStore leży tylko zaszyfrowany tekst. Bez tego klucz API był czytelny
 * w pliku ustawień i szedł do backupu Google.
 */
object SecureKey {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "kaori_api_keys"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val PREFIX = "enc:"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    /** Zwraca "enc:<base64(iv+cipher)>" albo pusty string, jeśli nie ma czego szyfrować. */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val all = ByteArray(iv.size + body.size)
            iv.copyInto(all)
            body.copyInto(all, iv.size)
            PREFIX + Base64.encodeToString(all, Base64.NO_WRAP)
        }.getOrDefault("")
    }

    /**
     * Odszyfrowuje. Stare, jawne wartości (sprzed szyfrowania) przechodzą bez zmian,
     * więc aktualizacja nie wylogowuje użytkownika.
     */
    fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        if (!stored.startsWith(PREFIX)) return stored
        return runCatching {
            val raw = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val body = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrDefault("")
    }
}
