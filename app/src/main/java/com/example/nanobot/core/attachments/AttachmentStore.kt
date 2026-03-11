package com.example.nanobot.core.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.example.nanobot.core.model.Attachment
import com.example.nanobot.core.model.AttachmentType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import android.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AttachmentStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val maxPreparedBytes = 1_500_000L
    private val maxImageDimension = 1_568

    suspend fun importImage(uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        require(mimeType.startsWith("image/")) { "Only image attachments are supported right now." }

        val metadata = queryMetadata(uri)
        val extension = mimeType.substringAfter('/', "jpg").substringBefore(';').ifBlank { "jpg" }
        val fileName = "${UUID.randomUUID()}.$extension"
        val relativePath = "attachments/images/$fileName"
        val destination = File(context.filesDir, relativePath).apply {
            parentFile?.mkdirs()
        }

        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Unable to read the selected image.")

        val sizeBytes = destination.length()
        Attachment(
            type = AttachmentType.IMAGE,
            displayName = metadata.displayName ?: destination.name,
            mimeType = mimeType,
            sizeBytes = if (sizeBytes > 0L) sizeBytes else metadata.sizeBytes,
            localPath = relativePath
        )
    }

    private val maxFileSizeBytes = 10_000_000L

    suspend fun importFile(uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
        val metadata = queryMetadata(uri)
        val extension = metadata.displayName?.substringAfterLast('.', "")
            ?.ifBlank { mimeType.substringAfter('/', "bin").substringBefore(';') }
            ?: mimeType.substringAfter('/', "bin").substringBefore(';')
        val fileName = "${UUID.randomUUID()}.$extension"
        val relativePath = "attachments/files/$fileName"
        val destination = File(context.filesDir, relativePath).apply {
            parentFile?.mkdirs()
        }

        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Unable to read the selected file.")

        val sizeBytes = destination.length()
        require(sizeBytes in 1..maxFileSizeBytes) {
            "File is too large (${sizeBytes} bytes). Maximum file size is ${maxFileSizeBytes / 1_000_000} MB."
        }

        Attachment(
            type = AttachmentType.FILE,
            displayName = metadata.displayName ?: destination.name,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            localPath = relativePath
        )
    }

    fun resolveFile(localPath: String): File = File(context.filesDir, localPath)

    suspend fun buildDataUrl(localPath: String, mimeType: String, maxBytes: Long = maxPreparedBytes): String = withContext(Dispatchers.IO) {
        val file = resolveFile(localPath)
        require(file.exists()) { "Attachment file is missing: $localPath" }
        val preparedBytes = if (mimeType.startsWith("image/")) {
            prepareImageBytes(file, mimeType, maxBytes)
        } else {
            require(file.length() in 1..maxBytes) {
                "Attachment '$localPath' is too large for multimodal upload right now."
            }
            file.readBytes()
        }
        val encoded = Base64.encodeToString(preparedBytes, Base64.NO_WRAP)
        "data:$mimeType;base64,$encoded"
    }

    suspend fun readFileAsText(localPath: String): String = withContext(Dispatchers.IO) {
        val file = resolveFile(localPath)
        require(file.exists()) { "Attachment file is missing: $localPath" }
        require(file.length() in 1..maxFileSizeBytes) {
            "Attachment '$localPath' is too large to read as text."
        }
        file.readText()
    }

    private fun prepareImageBytes(file: File, mimeType: String, maxBytes: Long): ByteArray {
        if (file.length() in 1..maxBytes) {
            return file.readBytes()
        }

        val original = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IllegalArgumentException("Unable to decode image attachment '${file.name}'.")
        val scaled = scaleBitmapIfNeeded(original)
        val format = if (mimeType.equals("image/png", ignoreCase = true)) {
            Bitmap.CompressFormat.PNG
        } else {
            Bitmap.CompressFormat.JPEG
        }

        var quality = if (format == Bitmap.CompressFormat.PNG) 100 else 90
        var compressed = compressBitmap(scaled, format, quality)
        while (compressed.size > maxBytes && format != Bitmap.CompressFormat.PNG && quality > 35) {
            quality -= 10
            compressed = compressBitmap(scaled, format, quality)
        }

        if (scaled !== original) scaled.recycle()
        original.recycle()

        require(compressed.size <= maxBytes) {
            "Attachment '${file.name}' is too large for multimodal upload even after image preparation."
        }
        return compressed
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val maxDimension = maxOf(bitmap.width, bitmap.height)
        if (maxDimension <= maxImageDimension) {
            return bitmap
        }

        val scale = maxImageDimension.toFloat() / maxDimension.toFloat()
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun compressBitmap(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        quality: Int
    ): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(format, quality, output)
        return output.toByteArray()
    }

    private fun queryMetadata(uri: Uri): AttachmentMetadata {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                return AttachmentMetadata(
                    displayName = if (nameIndex >= 0) cursor.getString(nameIndex) else null,
                    sizeBytes = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                )
            }
        }
        return AttachmentMetadata(displayName = null, sizeBytes = 0L)
    }

    private data class AttachmentMetadata(
        val displayName: String?,
        val sizeBytes: Long
    )
}
