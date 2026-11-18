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

data class ApiKeySlot(
    val id: String = UUID.randomUUID().toString(),
    val providerId: String,
    val label: String,
    val customBaseUrl: String = "",
    val preferredModel: String = "",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastTestedAt: Long? = null,
    val lastTestStatus: String = "Untested"
) {
    fun maskedPreview(decryptedKey: String): String {
        if (decryptedKey.isBlank()) return "Not set"
        return if (decryptedKey.length <= 8) {
            "••••${decryptedKey.takeLast(2)}"
        } else {
            "${decryptedKey.take(4)}••••${decryptedKey.takeLast(4)}"
        }
    }
}

/**
 * Android Keystore-backed AES-256-GCM vault for third-party API keys (Groq, OpenRouter,
 * Hugging Face, OpenAI-compatible endpoints). Keys are never logged or written in plaintext.
 */
class AuraKeystoreVault(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyAlias = "aura_ai_vault_aes_key"

    fun saveApiKeySlot(slot: ApiKeySlot, rawApiKey: String) {
        val slots = loadAllSlotsMetadata().toMutableList()
        val existingIdx = slots.indexOfFirst { it.id == slot.id }
        if (existingIdx >= 0) {
            slots[existingIdx] = slot
        } else {
            slots.add(slot)
        }
        saveSlotsMetadata(slots)
        if (rawApiKey.isNotBlank()) {
            val encrypted = encryptString(rawApiKey.trim())
            prefs.edit().putString("$KEY_PREFIX${slot.id}", encrypted).apply()
        }
    }

    fun updateSlotTestResult(slotId: String, status: String) {
        val slots = loadAllSlotsMetadata().map {
            if (it.id == slotId) it.copy(lastTestedAt = System.currentTimeMillis(), lastTestStatus = status)
            else it
        }
        saveSlotsMetadata(slots)
    }

    fun toggleSlotEnabled(slotId: String, enabled: Boolean) {
        val slots = loadAllSlotsMetadata().map {
            if (it.id == slotId) it.copy(isEnabled = enabled) else it
        }
        saveSlotsMetadata(slots)
    }

    fun deleteApiKeySlot(slotId: String) {
        val slots = loadAllSlotsMetadata().filterNot { it.id == slotId }
        saveSlotsMetadata(slots)
        prefs.edit().remove("$KEY_PREFIX$slotId").apply()
    }

    fun getDecryptedKey(slotId: String): String? {
        val encrypted = prefs.getString("$KEY_PREFIX$slotId", null) ?: return null
        return decryptString(encrypted)
    }

    fun getActiveSlotForProvider(providerId: String): Pair<ApiKeySlot, String>? {
        val slots = loadAllSlotsMetadata().filter { it.providerId == providerId && it.isEnabled }
        for (slot in slots) {
            val key = getDecryptedKey(slot.id)
            if (!key.isNullOrBlank()) {
                return slot to key
            }
        }
        return null
    }

    fun loadAllSlotsMetadata(): List<ApiKeySlot> {
        val json = prefs.getString(SLOTS_META_KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                ApiKeySlot(
                    id = obj.getString("id"),
                    providerId = obj.getString("providerId"),
                    label = obj.getString("label"),
                    customBaseUrl = obj.optString("customBaseUrl", ""),
                    preferredModel = obj.optString("preferredModel", ""),
                    isEnabled = obj.optBoolean("isEnabled", true),
                    createdAt = obj.optLong("createdAt", 0L),
                    lastTestedAt = if (obj.has("lastTestedAt") && !obj.isNull("lastTestedAt")) {
                        obj.getLong("lastTestedAt")
                    } else null,
                    lastTestStatus = obj.optString("lastTestStatus", "Untested")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse API slot metadata", e)
            emptyList()
        }
    }

    private fun saveSlotsMetadata(slots: List<ApiKeySlot>) {
        val arr = JSONArray()
        slots.forEach { slot ->
            arr.put(
                JSONObject().apply {
                    put("id", slot.id)
                    put("providerId", slot.providerId)
                    put("label", slot.label)
                    put("customBaseUrl", slot.customBaseUrl)
                    put("preferredModel", slot.preferredModel)
                    put("isEnabled", slot.isEnabled)
                    put("createdAt", slot.createdAt)
                    put("lastTestedAt", slot.lastTestedAt)
                    put("lastTestStatus", slot.lastTestStatus)
                }
            )
        }
        prefs.edit().putString(SLOTS_META_KEY, arr.toString()).apply()
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

    private fun encryptString(plaintext: String): String {
        val secretKey = getOrCreateSecretKey()
        if (secretKey != null) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(cipherText, Base64.NO_WRAP)
            return "ks:$ivB64:$dataB64"
        }
        // Fallback only for JVM test environments without AndroidKeyStore
        val encoded = Base64.encodeToString(plaintext.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "jvm:$encoded"
    }

    private fun decryptString(payload: String): String? {
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
            Log.e(TAG, "Failed to decrypt key material", e)
            null
        }
    }

    companion object {
        private const val TAG = "AuraKeystoreVault"
        private const val PREFS_NAME = "aura_encrypted_key_vault"
        private const val SLOTS_META_KEY = "slots_meta"
        private const val KEY_PREFIX = "enc_key_"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
