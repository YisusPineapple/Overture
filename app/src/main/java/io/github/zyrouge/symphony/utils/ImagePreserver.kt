package io.github.zyrouge.symphony.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.max

object ImagePreserver {
    enum class Quality(val maxSide: Int?) {
        Low(256),
        Medium(512),
        High(1024),
        Loseless(null),
    }

    fun resize(bitmap: Bitmap, quality: Quality): Bitmap {
        if (quality.maxSide == null || max(bitmap.width, bitmap.height) < quality.maxSide) {
            return bitmap
        }
        val (width, height) = calculateDimensions(bitmap.width, bitmap.height, quality.maxSide)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun calculateDimensions(width: Int, height: Int, maxSide: Int) = when {
        width > height -> maxSide to (height * (maxSide.toFloat() / width)).toInt()
        width < height -> (width * (maxSide.toFloat() / height)).toInt() to maxSide
        else -> maxSide to maxSide
    }

    // Overture: Calculate sample size to prevent OOM on huge Hi-Res covers
    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}