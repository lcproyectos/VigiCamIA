package com.vigicamia.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SnapshotHelper(private val context: Context) {
    private var lastCaptureTime = 0L

    fun captureIntruderPhoto(sourceBitmap: Bitmap): Boolean {
        val now = System.currentTimeMillis()
        // Guarda máximo 1 foto cada 6 segundos para no saturar la galería
        if (now - lastCaptureTime < 6000L) return false
        lastCaptureTime = now

        return try {
            val mutableBmp = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBmp)
            val timestampText = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("es", "CL")).format(Date(now))
            val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
            val fileName = "INTRUSO_$fileStamp.jpg"

            // Estampa fecha, hora y etiqueta en la foto
            val bgPaint = Paint().apply { color = Color.argb(170, 183, 28, 28) }
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = (mutableBmp.height * 0.04f).coerceAtLeast(24f)
                isFakeBoldText = true
                isAntiAlias = true
            }
            val barHeight = textPaint.textSize * 1.8f
            canvas.drawRect(0f, mutableBmp.height - barHeight, mutableBmp.width.toFloat(), mutableBmp.height.toFloat(), bgPaint)
            canvas.drawText("ALERTA INTRUSO • $timestampText", 20f, mutableBmp.height - (barHeight * 0.28f), textPaint)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VigiCamIA")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { out ->
                    mutableBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "VigiCamIA")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    mutableBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
