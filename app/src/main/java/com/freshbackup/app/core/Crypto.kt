package com.freshbackup.app.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM file encryption. Key is derived from the backup account id
 * plus an optional user passphrase, so backups restore on any device
 * signed in with the same account. APKs and wallpapers stay plaintext.
 */
object Crypto {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val ITERATIONS = 120_000

    fun deriveKey(userId: String, passphrase: String = ""): SecretKeySpec {
        val salt = ("freshbackup-$userId-v1").toByteArray()
        val password = if (passphrase.isEmpty()) userId else "$userId:$passphrase"
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    fun encrypt(input: File, output: File, userId: String, passphrase: String = "") {
        val key = deriveKey(userId, passphrase)
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        output.parentFile?.mkdirs()
        FileInputStream(input).use { fis ->
            FileOutputStream(output).use { fos ->
                fos.write(iv)
                val buf = ByteArray(64 * 1024)
                var n: Int
                while (fis.read(buf).also { n = it } != -1) {
                    cipher.update(buf, 0, n)?.let { fos.write(it) }
                }
                fos.write(cipher.doFinal())
            }
        }
    }

    fun decrypt(input: File, output: File, userId: String, passphrase: String = "") {
        val key = deriveKey(userId, passphrase)
        FileInputStream(input).use { fis ->
            val iv = ByteArray(IV_BYTES)
            check(fis.read(iv) == IV_BYTES) { "corrupt backup file" }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            output.parentFile?.mkdirs()
            FileOutputStream(output).use { fos ->
                val buf = ByteArray(64 * 1024)
                var n: Int
                while (fis.read(buf).also { n = it } != -1) {
                    cipher.update(buf, 0, n)?.let { fos.write(it) }
                }
                fos.write(cipher.doFinal())
            }
        }
    }

    fun encryptedName(name: String): String = "$name.enc"
}
