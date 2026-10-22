package com.fixedsource.mobile.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object WatermarkRenderer {

    private const val MAX_WIDTH = 1600f

    fun render(
        source: File,
        target: File,
        stampLine: String,
        entityLine: String,
        brandLine: String
    ): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("无法读取照片")
        }

        var sampleSize = 1
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        while (largest / sampleSize > 3200) sampleSize *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = BitmapFactory.decodeFile(source.absolutePath, options)
            ?: throw IOException("无法解码照片")
        bitmap = applyExifOrientation(source, bitmap)
        bitmap = scaleDown(bitmap)

        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            ?: throw IOException("无法创建水印画布")
        if (mutable !== bitmap) bitmap.recycle()

        val canvas = Canvas(mutable)
        val width = mutable.width.toFloat()
        val height = mutable.height.toFloat()
        val padding = width * 0.035f
        val stampSize = (width * 0.0215f).coerceAtLeast(21f)
        val entitySize = (width * 0.0255f).coerceAtLeast(25f)
        val brandSize = (width * 0.017f).coerceAtLeast(17f)
        val gap = height * 0.009f

        val brandY = height - padding
        val entityY = brandY - brandSize - gap - entitySize * 0.35f
        val stampY = entityY - entitySize * 1.2f - gap - stampSize * 0.35f
        val barTop = (stampY - stampSize * 1.55f).coerceAtLeast(0f)

        val gradient = LinearGradient(
            0f,
            barTop,
            0f,
            height,
            intArrayOf(
                Color.TRANSPARENT,
                0xA6061A22.toInt(),
                0xE6061A22.toInt()
            ),
            floatArrayOf(0f, 0.38f, 1f),
            Shader.TileMode.CLAMP
        )
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
        canvas.drawRect(0f, barTop, width, height, barPaint)

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF34D6C2.toInt()
        }
        canvas.drawRoundRect(
            padding,
            barTop + gap,
            padding + width * 0.0035f,
            height - gap,
            3f,
            3f,
            accentPaint
        )

        val textLeft = padding + width * 0.014f
        val maxTextWidth = width - textLeft - padding

        val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE6FFFFFF.toInt()
            textSize = stampSize
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val entityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = entitySize
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF7DEDDF.toInt()
            textSize = brandSize
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        canvas.drawText(
            fitText(stampPaint, stampLine, maxTextWidth),
            textLeft,
            stampY,
            stampPaint
        )
        canvas.drawText(
            fitText(entityPaint, entityLine, maxTextWidth),
            textLeft,
            entityY,
            entityPaint
        )
        canvas.drawText(
            fitText(brandPaint, brandLine, maxTextWidth),
            textLeft,
            brandY,
            brandPaint
        )

        target.parentFile?.mkdirs()
        FileOutputStream(target).use { output ->
            mutable.compress(Bitmap.CompressFormat.JPEG, 92, output)
        }
        mutable.recycle()
        return target
    }

    private fun fitText(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1) {
            val candidate = text.take(end) + "…"
            if (paint.measureText(candidate) <= maxWidth) return candidate
            end--
        }
        return "…"
    }

    private fun applyExifOrientation(source: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(source).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= MAX_WIDTH) return bitmap
        val ratio = MAX_WIDTH / bitmap.width
        val targetHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, MAX_WIDTH.toInt(), targetHeight, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
