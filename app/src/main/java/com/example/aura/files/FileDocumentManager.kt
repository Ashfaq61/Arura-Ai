package com.example.aura.files

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class LoadedDocumentResult(
    val displayName: String,
    val mimeType: String,
    val extractedText: String,
    val previewBitmap: Bitmap?,
    val pageCount: Int
)

class FileDocumentManager(private val context: Context) {

    suspend fun loadDocumentFromUri(uri: Uri): Result<LoadedDocumentResult> = withContext(Dispatchers.IO) {
        try {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "document"

            when {
                mime.contains("pdf", ignoreCase = true) || name.endsWith(".pdf", ignoreCase = true) -> {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                        ?: return@withContext Result.failure(Exception("Cannot open PDF file descriptor."))
                    pfd.use { fd ->
                        PdfRenderer(fd).use { renderer ->
                            val count = renderer.pageCount
                            val pageBitmap = if (count > 0) {
                                renderer.openPage(0).use { page ->
                                    val bmp = Bitmap.createBitmap(
                                        (page.width * 1.5f).toInt().coerceIn(400, 1400),
                                        (page.height * 1.5f).toInt().coerceIn(400, 1800),
                                        Bitmap.Config.ARGB_8888
                                    )
                                    val canvas = Canvas(bmp)
                                    canvas.drawColor(Color.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bmp
                                }
                            } else null
                            Result.success(
                                LoadedDocumentResult(
                                    displayName = name,
                                    mimeType = "application/pdf",
                                    extractedText = "PDF document '$name' ($count pages). Page 1 rendered for multimodal Gemini OCR & summarization.",
                                    previewBitmap = pageBitmap,
                                    pageCount = count
                                )
                            )
                        }
                    }
                }
                mime.startsWith("image/") -> {
                    val bmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                    Result.success(
                        LoadedDocumentResult(
                            displayName = name,
                            mimeType = mime,
                            extractedText = "Image '$name' loaded via Photo Picker for Gemini Vision OCR & analysis.",
                            previewBitmap = bmp,
                            pageCount = 1
                        )
                    )
                }
                else -> {
                    val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText().take(12_000)
                    }.orEmpty()
                    Result.success(
                        LoadedDocumentResult(
                            displayName = name,
                            mimeType = mime.ifBlank { "text/plain" },
                            extractedText = text.ifBlank { "Binary or empty document ($name)." },
                            previewBitmap = null,
                            pageCount = 1
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareSummary(title: String, body: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        }.getOrNull()
    }
}
