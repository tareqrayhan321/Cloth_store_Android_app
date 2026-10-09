package com.imran.clothstore.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

/**
 * মেমোর ছবি Drive-এ পাঠানোর আগে ছোট করা — লেখা পড়ার মতো স্পষ্ট থাকে (বড় পাশ ১৬০০px, JPEG ৮০)
 * কিন্তু সাইজ সাধারণত ২০০–৫০০KB। নির্বাচিত URI আগে একবার cache-এ কপি হয়, তাই provider-এর
 * একবার-ব্যবহারযোগ্য stream থেকেও decode করা যায়। Android 9+ এ ImageDecoder HEIC/নতুন format-ও পড়ে।
 */
object MemoImage {
    private const val MAX_SIDE = 1600
    private const val QUALITY = 80

    class Compressed(val bytes: ByteArray, val width: Int, val height: Int)

    @Throws(IOException::class)
    fun compress(resolver: ContentResolver, uri: Uri, cacheDir: File): Compressed {
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw IOException("অ্যাপের cache folder তৈরি করা যায়নি")
        }
        val source = File.createTempFile("memo-source-", ".img", cacheDir)
        try {
            val input = resolver.openInputStream(uri)
                ?: throw IOException("নির্বাচিত ছবিটি খোলা যায়নি; গ্যালারি থেকে আবার বেছে নিন")
            input.use { stream -> source.outputStream().use { stream.copyTo(it) } }
            if (source.length() <= 0L) {
                throw IOException("নির্বাচিত ছবিটি খালি বা পড়া যায়নি; গ্যালারি থেকে আবার বেছে নিন")
            }

            var bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                decodeModern(source)
            } else {
                decodeLegacy(source)
            }
            try {
                val longSide = maxOf(bitmap.width, bitmap.height)
                if (longSide > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longSide
                    val scaled = Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                        (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                        true
                    )
                    if (scaled !== bitmap) bitmap.recycle()
                    bitmap = scaled
                }

                val output = ByteArrayOutputStream()
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, output)) {
                    throw IOException("ছবিটি JPEG-এ রূপান্তর করা যায়নি")
                }
                return Compressed(output.toByteArray(), bitmap.width, bitmap.height)
            } finally {
                bitmap.recycle()
            }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("ছবির ফাইলটি খোলা বা decode করা যায়নি; গ্যালারি থেকে JPG/PNG ছবি আবার বেছে নিন", e)
        } finally {
            source.delete()
        }
    }

    /** ImageDecoder automatically applies image orientation and handles modern formats on API 28+. */
    private fun decodeModern(source: File): Bitmap {
        val imageSource = ImageDecoder.createSource(source)
        return ImageDecoder.decodeBitmap(imageSource) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val longSide = maxOf(width, height)
            if (longSide > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / longSide
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1)
                )
            }
        }
    }

    /** BitmapFactory fallback for API 24–27, with downsampling and JPEG EXIF rotation. */
    private fun decodeLegacy(source: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("ছবির format সমর্থিত নয়; JPG বা PNG ছবি বেছে নিন")
        }

        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxSide / (sample * 2) >= MAX_SIDE) sample *= 2
        var bitmap = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: throw IOException("ছবিটি decode করা যায়নি; গ্যালারি থেকে অন্য ছবি বেছে নিন")

        val degrees = try {
            when (ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Exception) {
            0f
        }
        if (degrees != 0f) {
            val rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true
            )
            if (rotated !== bitmap) bitmap.recycle()
            bitmap = rotated
        }
        return bitmap
    }
}
