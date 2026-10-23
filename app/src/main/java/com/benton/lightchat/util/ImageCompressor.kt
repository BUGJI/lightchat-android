package com.benton.lightchat.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageCompressor {

    data class Prepared(
        val file: File,
        val mime: String,
        val name: String,
    )

    suspend fun prepare(
        context: Context,
        uri: Uri,
        maxBytes: Long = DEFAULT_MAX_BYTES,
    ): Prepared? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: return@withContext null
        if (!mime.startsWith("image/") || mime == "image/gif") return@withContext null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }.getOrNull() ?: return@withContext null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_WIDTH ||
            bounds.outHeight / (sample * 2) >= MAX_HEIGHT
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() ?: return@withContext null

        var bitmap = rotate(decoded, orientation)
        bitmap = scaleDown(bitmap, MAX_WIDTH, MAX_HEIGHT)

        val output = File(context.cacheDir, "upload_${UUID.randomUUID()}.jpg")
        var quality = 85
        var attempts = 0
        try {
            while (true) {
                FileOutputStream(output).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                }
                if (output.length() in 1..maxBytes || attempts >= 8) break
                attempts += 1
                if (quality > 55) {
                    quality -= 10
                } else {
                    val scaled = scaleDown(
                        bitmap,
                        (bitmap.width * 0.85).toInt().coerceAtLeast(320),
                        (bitmap.height * 0.85).toInt().coerceAtLeast(320),
                    )
                    if (scaled !== bitmap) {
                        bitmap.recycle()
                        bitmap = scaled
                    }
                }
            }
        } finally {
            bitmap.recycle()
        }

        if (!output.exists() || output.length() <= 0) {
            output.delete()
            return@withContext null
        }

        val baseName = displayName(resolver, uri)?.substringBeforeLast('.')?.take(40)
            ?.takeIf { it.isNotBlank() }
            ?: "image_${System.currentTimeMillis()}"
        Prepared(output, "image/jpeg", "$baseName.jpg")
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleDown(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) return bitmap
        val ratio = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private const val MAX_WIDTH = 1920
    private const val MAX_HEIGHT = 1080
    private const val DEFAULT_MAX_BYTES = 2L * 1024 * 1024
}
