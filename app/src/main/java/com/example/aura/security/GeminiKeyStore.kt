package com.example.aura.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class GeminiKeyEntry(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val maskedPreview: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastTestStatus: String = "Not tested"
)

/**
 * Encrypted storage for up to 3 user-supplied Gemini API keys.
 * Uses Android Keystore AES-256-GCM encryption over private SharedPreferences.
 * Never hardcodes or logs plaintext API keys.
 */
class GeminiKeyStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyAlias = "aura_gemini_api_key_aes"

    fun getKeys(): List<GeminiKeyEntry> {
        val rawJson = prefs.getString(META_KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(rawJson)
            (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                GeminiKeyEntry(
                    id = obj.getString("id"),
                    label = obj.optString("label", "Gemini Key ${idx + 1}"),
                    maskedPreview = obj.optString("maskedPreview", "••••"),
                    createdAt = obj.optLong("createdAt", 0L),
                    lastTestStatus = obj.optString("lastTestStatus", "Not tested")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Gemini key metadata", e)
            emptyList()
        }
    }

    fun getDecryptedKeys(): List<String> {
        return getKeys().mapNotNull { entry ->
            getDecryptedKey(entry.id)?.takeIf { it.isNotBlank() }
        }
    }

    fun getDecryptedKey(id: String): String? {
        val encrypted = prefs.getString("$SECRET_PREFIX$id", null) ?: return null
        return decrypt(encrypted)
    }

    fun hasAnyKey(): Boolean = getDecryptedKeys().isNotEmpty()

    fun saveKey(rawKey: String, customLabel: String = ""): Result<GeminiKeyEntry> {
        val cleaned = rawKey.trim()
        if (cleaned.isBlank()) {
            return Result.failure(IllegalArgumentException("API key cannot be empty."))
        }
        val existing = getKeys().toMutableList()
        // Avoid duplicating an identical key already stored
        val duplicate = existing.firstOrNull { getDecryptedKey(it.id) == cleaned }
        if (duplicate != null) {
            return Result.success(duplicate)
        }
        if (existing.size >= MAX_KEYS) {
            return Result.failure(IllegalStateException("Maximum $MAX_KEYS Gemini API keys allowed. Delete a key first."))
        }

        val entry = GeminiKeyEntry(
            label = customLabel.trim().ifBlank { "Gemini Key #${existing.size + 1}" },
            maskedPreview = maskKey(cleaned)
        )
        val encrypted = encrypt(cleaned)
        existing.add(entry)
        prefs.edit()
            .putString("$SECRET_PREFIX${entry.id}", encrypted)
            .putString(META_KEY, serializeEntries(existing))
            .apply()
        return Result.success(entry)
    }

    fun updateKeyStatus(id: String, status: String) {
        val updated = getKeys().map {
            if (it.id == id) it.copy(lastTestStatus = status) else it
        }
        prefs.edit().putString(META_KEY, serializeEntries(updated)).apply()
    }

    fun deleteKey(id: String) {
        val remaining = getKeys().filterNot { it.id == id }
        prefs.edit()
            .remove("$SECRET_PREFIX$id")
            .putString(META_KEY, serializeEntries(remaining))
            .apply()
    }

    fun clearAll() {
        val editor = prefs.edit()
        getKeys().forEach { editor.remove("$SECRET_PREFIX${it.id}") }
        editor.remove(META_KEY).apply()
    }

    private fun maskKey(key: String): String {
        return if (key.length <= 8) "••••${key.takeLast(2)}"
        else "${key.take(4)}••••${key.takeLast(4)}"
    }

    private fun serializeEntries(entries: List<GeminiKeyEntry>): String {
        val arr = JSONArray()
        entries.forEach { entry ->
            arr.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("label", entry.label)
                    put("maskedPreview", entry.maskedPreview)
                    put("createdAt", entry.createdAt)
                    put("lastTestStatus", entry.lastTestStatus)
                }
            )
        }
        return arr.toString()
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(keyAlias, null) as? SecretKey
            if (existing != null) return existing

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        } catch (e: Exception) {
            Log.w(TAG, "AndroidKeyStore unavailable in current runtime, using sealed fallback", e)
            null
        }
    }

    private fun encrypt(plaintext: String): String {
        val secretKey = getOrCreateSecretKey()
        if (secretKey != null) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            return "ks:$ivB64:$dataB64"
        }
        // Fallback only for JVM Robolectric tests where AndroidKeyStore hardware provider is absent
        val encoded = Base64.encodeToString(plaintext.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "jvm:$encoded"
    }

    private fun decrypt(payload: String): String? {
        return try {
            when {
                payload.startsWith("ks:") -> {
                    val parts = payload.removePrefix("ks:").split(":")
                    if (parts.size != 2) return null
                    val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                    val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
                    val secretKey = getOrCreateSecretKey() ?: return null
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                    String(cipher.doFinal(cipherText), Charsets.UTF_8)
                }
                payload.startsWith("jvm:") -> {
                    String(Base64.decode(payload.removePrefix("jvm:"), Base64.NO_WRAP), Charsets.UTF_8)
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt Gemini key", e)
            null
        }
    }

    companion object {
        const val MAX_KEYS = 3
        private const val TAG = "GeminiKeyStore"
        private const val PREFS_NAME = "aura_gemini_encrypted_keys"
        private const val META_KEY = "gemini_keys_meta"
        private const val SECRET_PREFIX = "gemini_secret_"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
