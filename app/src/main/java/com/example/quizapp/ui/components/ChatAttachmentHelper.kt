package com.example.quizapp.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.example.quizapp.data.model.ChatAttachment
import com.example.quizapp.data.model.ChatAttachmentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

object ChatAttachmentHelper {

    private const val MAX_IMAGE_DIMENSION = 1280
    private const val MAX_TEXT_CHARS = 30_000
    private const val MAX_PDF_BYTES = 8 * 1024 * 1024
    private const val MAX_PDF_PAGES_RENDER = 4

    suspend fun loadImageAttachment(context: Context, uri: Uri): ChatAttachment? = withContext(Dispatchers.IO) {
        val meta = queryUriMeta(context, uri)
        val fileName = meta.first.ifBlank { "photo_${System.currentTimeMillis()}.jpg" }
        val base64 = encodeImageUriToJpegBase64(context, uri) ?: return@withContext null
        ChatAttachment(
            name = fileName,
            mimeType = "image/jpeg",
            type = ChatAttachmentType.IMAGE,
            uriString = uri.toString(),
            base64Data = base64,
            sizeBytes = meta.second
        )
    }

    suspend fun loadDocumentAttachment(context: Context, uri: Uri): Result<ChatAttachment> = withContext(Dispatchers.IO) {
        try {
            val (rawName, sizeBytes) = queryUriMeta(context, uri)
            val fileName = rawName.ifBlank { "document" }
            val lowerName = fileName.lowercase()
            val detectedMime = context.contentResolver.getType(uri) ?: guessMimeFromExtension(lowerName)

            // 1. Image picked via document picker
            if (detectedMime.startsWith("image/") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") || lowerName.endsWith(".webp")
            ) {
                val imgBase64 = encodeImageUriToJpegBase64(context, uri)
                    ?: return@withContext Result.failure(IllegalArgumentException("Не удалось прочитать изображение «$fileName»."))
                return@withContext Result.success(
                    ChatAttachment(
                        name = fileName,
                        mimeType = "image/jpeg",
                        type = ChatAttachmentType.IMAGE,
                        uriString = uri.toString(),
                        base64Data = imgBase64,
                        sizeBytes = sizeBytes
                    )
                )
            }

            // 2. PDF file
            if (detectedMime == "application/pdf" || lowerName.endsWith(".pdf")) {
                val pdfBytes = readBytesLimited(context, uri, MAX_PDF_BYTES)
                val pdfBase64 = if (pdfBytes != null && pdfBytes.size <= MAX_PDF_BYTES) {
                    Base64.encodeToString(pdfBytes, Base64.NO_WRAP)
                } else null
                val renderedPages = renderPdfPagesToBase64(context, uri, MAX_PDF_PAGES_RENDER)
                if (pdfBase64 == null && renderedPages.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Не удалось обработать PDF «$fileName» (возможно, файл повреждён или слишком большой).")
                    )
                }
                return@withContext Result.success(
                    ChatAttachment(
                        name = fileName,
                        mimeType = "application/pdf",
                        type = ChatAttachmentType.FILE,
                        uriString = uri.toString(),
                        base64Data = pdfBase64,
                        pdfPageImagesBase64 = renderedPages,
                        extractedText = if (renderedPages.isNotEmpty()) {
                            "[PDF-документ «$fileName», страниц для анализа: ${renderedPages.size}]"
                        } else {
                            "[PDF-документ «$fileName»]"
                        },
                        sizeBytes = sizeBytes
                    )
                )
            }

            // 3. DOCX Word document
            if (lowerName.endsWith(".docx") ||
                detectedMime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ) {
                val docText = extractDocxText(context, uri)
                if (docText.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("В файле «$fileName» не найден текст."))
                }
                return@withContext Result.success(
                    ChatAttachment(
                        name = fileName,
                        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        type = ChatAttachmentType.FILE,
                        uriString = uri.toString(),
                        extractedText = truncateText(docText),
                        sizeBytes = sizeBytes
                    )
                )
            }

            // 4. Text-based files (.txt, .csv, .tsv, .json, .md, .xml, .html, code, etc.)
            if (isTextLikeFile(detectedMime, lowerName)) {
                val rawText = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    String(bytes, StandardCharsets.UTF_8).removePrefix("\uFEFF")
                }.orEmpty()
                if (rawText.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Файл «$fileName» пуст."))
                }
                return@withContext Result.success(
                    ChatAttachment(
                        name = fileName,
                        mimeType = detectedMime.ifBlank { "text/plain" },
                        type = ChatAttachmentType.FILE,
                        uriString = uri.toString(),
                        extractedText = truncateText(rawText),
                        sizeBytes = sizeBytes
                    )
                )
            }

            Result.failure(
                IllegalArgumentException(
                    "Формат файла «$fileName» не поддерживается. Можно прикреплять фото, PDF, DOCX, TXT, MD, CSV, TSV, JSON и код."
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Ошибка чтения файла: ${e.localizedMessage ?: "неизвестная ошибка"}"))
        }
    }

    private fun queryUriMeta(context: Context, uri: Uri): Pair<String, Long> {
        var name = ""
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx).orEmpty()
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {}
        if (name.isBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/') ?: ""
        }
        return name to size
    }

    private fun guessMimeFromExtension(lowerName: String): String = when {
        lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".log") -> "text/plain"
        lowerName.endsWith(".csv") -> "text/csv"
        lowerName.endsWith(".tsv") -> "text/tab-separated-values"
        lowerName.endsWith(".json") -> "application/json"
        lowerName.endsWith(".xml") -> "application/xml"
        lowerName.endsWith(".html") || lowerName.endsWith(".htm") -> "text/html"
        lowerName.endsWith(".pdf") -> "application/pdf"
        lowerName.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") -> "image/jpeg"
        lowerName.endsWith(".png") -> "image/png"
        lowerName.endsWith(".webp") -> "image/webp"
        else -> ""
    }

    private fun isTextLikeFile(mime: String, lowerName: String): Boolean {
        if (mime.startsWith("text/")) return true
        if (mime == "application/json" || mime == "application/xml" || mime == "application/csv") return true
        val textExts = listOf(
            ".txt", ".md", ".csv", ".tsv", ".json", ".xml", ".html", ".htm",
            ".kt", ".java", ".py", ".js", ".ts", ".jsx", ".tsx", ".c", ".cpp",
            ".h", ".cs", ".go", ".rs", ".sql", ".yaml", ".yml", ".ini", ".cfg", ".log"
        )
        return textExts.any { lowerName.endsWith(it) }
    }

    private fun truncateText(text: String): String {
        val clean = text.trim()
        return if (clean.length > MAX_TEXT_CHARS) {
            clean.take(MAX_TEXT_CHARS) + "\n\n[...текст файла сокращён до $MAX_TEXT_CHARS символов...]"
        } else {
            clean
        }
    }

    private fun readBytesLimited(context: Context, uri: Uri, maxBytes: Int): ByteArray? {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(chunk)
                if (read == -1) break
                total += read
                if (total > maxBytes) return@use null
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        }
    }

    private fun extractDocxText(context: Context, uri: Uri): String {
        val sb = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val xmlBytes = zip.readBytes()
                        val factory = XmlPullParserFactory.newInstance()
                        factory.isNamespaceAware = false
                        val parser = factory.newPullParser()
                        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            when (event) {
                                XmlPullParser.START_TAG -> {
                                    if (parser.name == "w:t" || parser.name == "t") {
                                        sb.append(parser.nextText())
                                    } else if (parser.name == "w:tab" || parser.name == "tab") {
                                        sb.append("\t")
                                    } else if (parser.name == "w:br" || parser.name == "br") {
                                        sb.append("\n")
                                    }
                                }
                                XmlPullParser.END_TAG -> {
                                    if (parser.name == "w:p" || parser.name == "p") {
                                        if (sb.isNotEmpty() && !sb.endsWith("\n")) {
                                            sb.append("\n")
                                        }
                                    } else if (parser.name == "w:tc" || parser.name == "tc") {
                                        sb.append(" | ")
                                    }
                                }
                            }
                            event = parser.next()
                        }
                        break
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return sb.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun renderPdfPagesToBase64(context: Context, uri: Uri, maxPages: Int): List<String> {
        val result = mutableListOf<String>()
        var pfd: android.os.ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
            renderer = PdfRenderer(pfd)
            val pageCount = minOf(renderer.pageCount, maxPages)
            for (i in 0 until pageCount) {
                var page: PdfRenderer.Page? = null
                try {
                    page = renderer.openPage(i)
                    val ratio = minOf(1280f / page.width.coerceAtLeast(1), 1280f / page.height.coerceAtLeast(1), 2f)
                    val targetW = (page.width * ratio).toInt().coerceAtLeast(1)
                    val targetH = (page.height * ratio).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    Canvas(bitmap).drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                    bitmap.recycle()
                    result.add(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
                } finally {
                    page?.close()
                }
            }
        } catch (_: Exception) {
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
        return result
    }

    private fun encodeImageUriToJpegBase64(context: Context, uri: Uri): String? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > MAX_IMAGE_DIMENSION * 2 ||
                boundsOpts.outHeight / sampleSize > MAX_IMAGE_DIMENSION * 2
            ) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            val rotation = readExifRotation(context, uri)
            val oriented = if (rotation != 0f) {
                val m = Matrix().apply { postRotate(rotation) }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
                if (rotated != decoded) decoded.recycle()
                rotated
            } else {
                decoded
            }

            val scaled = if (oriented.width > MAX_IMAGE_DIMENSION || oriented.height > MAX_IMAGE_DIMENSION) {
                val ratio = minOf(
                    MAX_IMAGE_DIMENSION.toFloat() / oriented.width,
                    MAX_IMAGE_DIMENSION.toFloat() / oriented.height
                )
                val w = (oriented.width * ratio).toInt().coerceAtLeast(1)
                val h = (oriented.height * ratio).toInt().coerceAtLeast(1)
                val s = Bitmap.createScaledBitmap(oriented, w, h, true)
                if (s != oriented) oriented.recycle()
                s
            } else {
                oriented
            }

            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            scaled.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun readExifRotation(context: Context, uri: Uri): Float {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) {
            0f
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val kb = bytes / 1024.0
        return if (kb < 1024.0) {
            "${kb.toInt().coerceAtLeast(1)} КБ"
        } else {
            String.format(java.util.Locale.US, "%.1f МБ", kb / 1024.0)
        }
    }
}
