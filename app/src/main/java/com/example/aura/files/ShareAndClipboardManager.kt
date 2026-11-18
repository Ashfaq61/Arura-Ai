package com.example.aura.files

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import com.example.aura.security.PrivacyDataRouter
import com.example.aura.security.PromptInjectionDefender

data class SharedContentPayload(
    val id: Long = System.currentTimeMillis(),
    val source: String, // "SHARESHEET" or "CLIPBOARD"
    val mimeType: String,
    val subject: String = "",
    val textContent: String = "",
    val sanitizedText: String = "",
    val extractedUrls: List<String> = emptyList(),
    val attachedUris: List<Uri> = emptyList(),
    val previewBitmap: Bitmap? = null,
    val injectionWarning: String? = null
)

/**
 * Handles Android Sharesheet intents (ACTION_SEND, ACTION_SEND_MULTIPLE) for text, URLs,
 * images, and documents, as well as user-triggered Clipboard reading and sanitization.
 */
class ShareAndClipboardManager(
    private val context: Context,
    private val fileManager: FileDocumentManager,
    private val privacyRouter: PrivacyDataRouter = PrivacyDataRouter(),
    private val injectionDefender: PromptInjectionDefender = PromptInjectionDefender()
) {
    private val urlRegex = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

    fun parseShareIntent(intent: Intent?): SharedContentPayload? {
        if (intent == null) return null
        val action = intent.action ?: return null
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null

        val mime = intent.type ?: "text/plain"
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()

        val uris = mutableListOf<Uri>()
        if (action == Intent.ACTION_SEND) {
            getParcelableUri(intent, Intent.EXTRA_STREAM)?.let { uris.add(it) }
        } else {
            uris.addAll(getParcelableUriList(intent, Intent.EXTRA_STREAM))
        }

        val loadedText = if (extraText.isNotBlank()) extraText
        else if (uris.isNotEmpty()) "Shared URIs (${uris.size}): ${uris.joinToString { it.lastPathSegment ?: it.toString() }}"
        else ""

        if (loadedText.isBlank() && uris.isEmpty()) return null
        return buildPayload(
            source = "SHARESHEET",
            mimeType = mime,
            subject = subject,
            rawText = loadedText,
            uris = uris,
            bitmap = null
        )
    }

    suspend fun parseShareIntentAsync(intent: Intent?): SharedContentPayload? {
        val base = parseShareIntent(intent) ?: return null
        val firstUri = base.attachedUris.firstOrNull() ?: return base
        val doc = fileManager.loadDocumentFromUri(firstUri).getOrNull() ?: return base
        val mergedText = buildString {
            if (base.textContent.isNotBlank() && !base.textContent.startsWith("Shared URIs")) {
                appendLine(base.textContent)
                appendLine()
            }
            append("[File: ${doc.displayName} (${doc.mimeType}, ${doc.pageCount}p)]\n${doc.extractedText}")
        }
        return buildPayload(
            source = "SHARESHEET",
            mimeType = doc.mimeType.ifBlank { base.mimeType },
            subject = base.subject,
            rawText = mergedText,
            uris = base.attachedUris,
            bitmap = doc.previewBitmap
        )
    }

    fun readClipboardOnUserAction(): Result<SharedContentPayload> {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return Result.failure(IllegalStateException("ClipboardManager unavailable."))
        if (!cm.hasPrimaryClip()) {
            return Result.failure(IllegalStateException("Clipboard is empty (or hidden until app focus)."))
        }
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) {
            return Result.failure(IllegalStateException("No readable clipboard items found."))
        }
        val item = clip.getItemAt(0)
        val rawText = item.coerceToText(context)?.toString().orEmpty()
        val uri = item.uri
        if (rawText.isBlank() && uri == null) {
            return Result.failure(IllegalStateException("Clipboard item contains no text or URI."))
        }
        val finalText = if (rawText.isNotBlank()) rawText else "Clipboard URI: $uri"
        return Result.success(
            buildPayload(
                source = "CLIPBOARD",
                mimeType = "text/plain",
                subject = "",
                rawText = finalText,
                uris = listOfNotNull(uri),
                bitmap = null
            )
        )
    }

    private fun buildPayload(
        source: String,
        mimeType: String,
        subject: String,
        rawText: String,
        uris: List<Uri>,
        bitmap: Bitmap?
    ): SharedContentPayload {
        val urls = urlRegex.findAll(rawText).map { it.value }.distinct().toList()
        val injection = injectionDefender.scanUntrustedContent(source, rawText)
        val redacted = privacyRouter.sanitizeForExternalProvider(rawText, redactEnabled = true)
        return SharedContentPayload(
            source = source,
            mimeType = mimeType,
            subject = subject,
            textContent = rawText,
            sanitizedText = redacted.redactedText,
            extractedUrls = urls,
            attachedUris = uris,
            previewBitmap = bitmap,
            injectionWarning = if (injection.isSuspicious) {
                "Prompt-injection signals (${injection.riskScore}/100): ${injection.matchedSignals.joinToString()}"
            } else null
        )
    }

    @Suppress("DEPRECATION")
    private fun getParcelableUri(intent: Intent, key: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, Uri::class.java)
        } else {
            intent.getParcelableExtra(key)
        }
    }

    @Suppress("DEPRECATION")
    private fun getParcelableUriList(intent: Intent, key: String): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(key, Uri::class.java).orEmpty()
        } else {
            intent.getParcelableArrayListExtra<Uri>(key).orEmpty()
        }
    }
}
