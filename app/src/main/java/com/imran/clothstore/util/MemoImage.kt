package com.imran.clothstore.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * মেমোর ছবি Drive-এ পাঠানোর আগে ছোট করা — লেখা পড়ার মতো স্পষ্ট থাকে (বড় পাশ ১৬০০px, JPEG ৮০)
 * কিন্তু সাইজ সাধারণত ২০০–৫০০KB। ক্যামেরার ছবির EXIF ঘোরানোও এখানে প্রয়োগ হয়।
 */
object MemoImage {
    private const val MAX_SIDE = 1600
    private const val QUALITY = 80

    class Compressed(val bytes: ByteArray, val width: Int, val height: Int)

    fun compress(resolver: ContentResolver, uri: Uri): Compressed? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxSide / (sample * 2) >= MAX_SIDE) sample *= 2

        var bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        // ঠিক MAX_SIDE-এ নামানো
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide > MAX_SIDE) {
            val scale = MAX_SIDE.toFloat() / longSide
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        }

        val degrees = try {
            resolver.openInputStream(uri)?.use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }
        if (degrees != 0f) {
            val m = Matrix().apply { postRotate(degrees) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        return Compressed(out.toByteArray(), bitmap.width, bitmap.height)
    }
}
