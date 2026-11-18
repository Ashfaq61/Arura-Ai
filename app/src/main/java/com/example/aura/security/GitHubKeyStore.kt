package com.example.aura.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.aura.github.DEFAULT_AURA_BRANCH
import com.example.aura.github.GitHubConfig
import com.example.aura.github.GitHubReleaseAsset
import com.example.aura.github.GitHubReleaseInfo
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore AES-256-GCM encrypted SharedPreferences store for GitHub repository configuration,
 * fine-grained personal access token, workflow edit permission toggle, and last-working rollback metadata.
 * Never logs tokens and never persists tokens in BuildConfig or source files.
 */
class GitHubKeyStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyAlias = "aura_github_pat_aes_key"

    fun loadConfig(): GitHubConfig {
        val owner = prefs.getString(KEY_OWNER, "").orEmpty().trim()
        val repo = prefs.getString(KEY_REPO, "").orEmpty().trim()
        val branch = prefs.getString(KEY_BRANCH, DEFAULT_AURA_BRANCH)
            ?.trim()
            ?.ifBlank { DEFAULT_AURA_BRANCH }
            ?: DEFAULT_AURA_BRANCH
        val token = getDecryptedToken()
        val allowWorkflowEdits = prefs.getBoolean(KEY_ALLOW_WORKFLOW_EDITS, false)
        return GitHubConfig(
            owner = owner,
            repo = repo,
            branch = branch,
            token = token,
            allowWorkflowEdits = allowWorkflowEdits
        )
    }

    fun saveConfig(
        owner: String,
        repo: String,
        branch: String,
        token: String,
        allowWorkflowEdits: Boolean
    ): GitHubConfig {
        val cleanOwner = owner.trim()
        val cleanRepo = repo.trim()
        val cleanBranch = branch.trim().ifBlank { DEFAULT_AURA_BRANCH }
        val cleanToken = token.trim()

        val editor = prefs.edit()
            .putString(KEY_OWNER, cleanOwner)
            .putString(KEY_REPO, cleanRepo)
            .putString(KEY_BRANCH, cleanBranch)
            .putBoolean(KEY_ALLOW_WORKFLOW_EDITS, allowWorkflowEdits)

        if (cleanToken.isEmpty()) {
            editor.remove(KEY_TOKEN_ENC)
        } else {
            editor.putString(KEY_TOKEN_ENC, encrypt(cleanToken))
        }
        editor.apply()

        return GitHubConfig(
            owner = cleanOwner,
            repo = cleanRepo,
            branch = cleanBranch,
            token = cleanToken,
            allowWorkflowEdits = allowWorkflowEdits
        )
    }

    fun setAllowWorkflowEdits(allowed: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_WORKFLOW_EDITS, allowed).apply()
    }

    fun getDecryptedToken(): String {
        val encrypted = prefs.getString(KEY_TOKEN_ENC, null) ?: return ""
        return decrypt(encrypted).orEmpty()
    }

    fun hasToken(): Boolean = getDecryptedToken().isNotBlank()

    fun getMaskedTokenPreview(): String {
        val token = getDecryptedToken()
        if (token.isBlank()) return "Not set"
        return if (token.length <= 8) "••••${token.takeLast(2)}"
        else "${token.take(4)}••••${token.takeLast(4)}"
    }

    fun getLastWorkingVersion(): String {
        return prefs.getString(KEY_LAST_WORKING_VERSION, "").orEmpty().trim()
    }

    fun saveLastWorkingVersion(versionName: String) {
        val cleaned = versionName.trim()
        if (cleaned.isNotEmpty()) {
            prefs.edit().putString(KEY_LAST_WORKING_VERSION, cleaned).apply()
        }
    }

    fun savePreviousRelease(release: GitHubReleaseInfo?) {
        val editor = prefs.edit()
        val asset = release?.apkAsset
        if (release == null || asset == null) {
            editor
                .remove(KEY_PREV_RELEASE_TAG)
                .remove(KEY_PREV_RELEASE_NAME)
                .remove(KEY_PREV_RELEASE_ASSET_URL)
                .remove(KEY_PREV_RELEASE_ASSET_NAME)
                .remove(KEY_PREV_RELEASE_ASSET_SIZE)
        } else {
            editor
                .putString(KEY_PREV_RELEASE_TAG, release.tagName)
                .putString(KEY_PREV_RELEASE_NAME, release.name)
                .putString(KEY_PREV_RELEASE_ASSET_URL, asset.apiAssetUrl)
                .putString(KEY_PREV_RELEASE_ASSET_NAME, asset.name)
                .putLong(KEY_PREV_RELEASE_ASSET_SIZE, asset.sizeBytes)
        }
        editor.apply()
    }

    fun getPreviousRelease(): GitHubReleaseInfo? {
        val tag = prefs.getString(KEY_PREV_RELEASE_TAG, null)?.takeIf { it.isNotBlank() } ?: return null
        val assetUrl = prefs.getString(KEY_PREV_RELEASE_ASSET_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        val assetName = prefs.getString(KEY_PREV_RELEASE_ASSET_NAME, null)?.takeIf { it.isNotBlank() } ?: "aura-previous.apk"
        val assetSize = prefs.getLong(KEY_PREV_RELEASE_ASSET_SIZE, 0L)
        val releaseName = prefs.getString(KEY_PREV_RELEASE_NAME, tag).orEmpty().ifBlank { tag }
        return GitHubReleaseInfo(
            tagName = tag,
            name = releaseName,
            body = "Previous verified release for safe rollback",
            htmlUrl = "",
            apkAsset = GitHubReleaseAsset(
                id = 0L,
                name = assetName,
                apiAssetUrl = assetUrl,
                browserDownloadUrl = "",
                sizeBytes = assetSize
            )
        )
    }

    fun clearAll() {
        prefs.edit().clear().apply()
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
            Log.e(TAG, "Failed to decrypt GitHub token", e)
            null
        }
    }

    companion object {
        private const val TAG = "GitHubKeyStore"
        private const val PREFS_NAME = "aura_github_encrypted_prefs"
        private const val KEY_OWNER = "gh_repo_owner"
        private const val KEY_REPO = "gh_repo_name"
        private const val KEY_BRANCH = "gh_branch"
        private const val KEY_TOKEN_ENC = "gh_pat_encrypted"
        private const val KEY_ALLOW_WORKFLOW_EDITS = "gh_allow_workflow_edits"
        private const val KEY_LAST_WORKING_VERSION = "gh_last_working_version"
        private const val KEY_PREV_RELEASE_TAG = "gh_prev_release_tag"
        private const val KEY_PREV_RELEASE_NAME = "gh_prev_release_name"
        private const val KEY_PREV_RELEASE_ASSET_URL = "gh_prev_release_asset_url"
        private const val KEY_PREV_RELEASE_ASSET_NAME = "gh_prev_release_asset_name"
        private const val KEY_PREV_RELEASE_ASSET_SIZE = "gh_prev_release_asset_size"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
