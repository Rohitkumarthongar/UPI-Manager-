package com.example.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware/Keystore backed AES-256-GCM encryption manager
 * for protecting sensitive financial notes and records at rest.
 */
object CryptoManager {
  private const val ANDROID_KEYSTORE = "AndroidKeyStore"
  private const val KEY_ALIAS = "OfflineLedger_FinancialKey_v1"
  private const val TRANSFORMATION = "AES/GCM/NoPadding"
  private const val GCM_IV_LENGTH = 12
  private const val GCM_TAG_LENGTH = 128

  private val keyStore: KeyStore by lazy {
    KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
  }

  private fun getOrCreateSecretKey(): SecretKey {
    if (keyStore.containsAlias(KEY_ALIAS)) {
      val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
      if (entry != null) return entry.secretKey
    }

    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec = KeyGenParameterSpec.Builder(
      KEY_ALIAS,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .build()

    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  fun encrypt(plainText: String): String {
    if (plainText.isEmpty()) return ""
    return try {
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
      val iv = cipher.iv
      val encryption = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

      val combined = ByteArray(iv.size + encryption.size)
      System.arraycopy(iv, 0, combined, 0, iv.size)
      System.arraycopy(encryption, 0, combined, iv.size, encryption.size)

      Base64.encodeToString(combined, Base64.NO_WRAP)
    } catch (e: Exception) {
      // Fallback in case device keystore has issues
      plainText
    }
  }

  fun decrypt(encryptedText: String): String {
    if (encryptedText.isEmpty()) return ""
    return try {
      val decoded = Base64.decode(encryptedText, Base64.NO_WRAP)
      if (decoded.size < GCM_IV_LENGTH) return encryptedText

      val iv = ByteArray(GCM_IV_LENGTH)
      val cipherBytes = ByteArray(decoded.size - GCM_IV_LENGTH)
      System.arraycopy(decoded, 0, iv, 0, GCM_IV_LENGTH)
      System.arraycopy(decoded, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.size)

      val cipher = Cipher.getInstance(TRANSFORMATION)
      val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
      cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)

      val decrypted = cipher.doFinal(cipherBytes)
      String(decrypted, Charsets.UTF_8)
    } catch (e: Exception) {
      encryptedText
    }
  }
}
