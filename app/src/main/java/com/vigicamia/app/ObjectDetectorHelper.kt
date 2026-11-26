package com.vigicamia.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.File
import java.io.FileOutputStream
import java.net.URL

data class DetectionResult(
    val categoryEs: String,
    val rawLabel: String,
    val score: Float,
    val boundingBox: RectF,
    val isPerson: Boolean
)

class ObjectDetectorHelper(private val context: Context) {
    private var detector: ObjectDetector? = null

    private val vehicles = setOf("car", "motorcycle", "bus", "truck", "bicycle")
    private val animals = setOf("dog", "cat", "bird", "horse", "sheep", "cow")

    fun initialize(): Boolean {
        return try {
            val modelFile = File(context.filesDir, "efficientdet_lite0.tflite")
            if (!modelFile.exists()) {
                val url = URL("https://googleapis.com")
                url.openStream().use { input ->
                    FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setMaxResults(5)
                .setScoreThreshold(0.52f)
                .build()
            detector = ObjectDetector.createFromFileAndOptions(modelFile, options)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        val currentDetector = detector ?: return emptyList()
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val results = currentDetector.detect(tensorImage)

        return results.mapNotNull { detection ->
            val category = detection.categories.firstOrNull() ?: return@mapNotNull null
            val label = category.label.lowercase()
            val isPerson = label == "person"
            val categoryEs = when {
                isPerson -> "PERSONA"
                label in vehicles -> "VEHÍCULO ($label)"
                label in animals -> "ANIMAL ($label)"
                else -> "OBJETO ($label)"
            }
            DetectionResult(
                categoryEs = categoryEs,
                rawLabel = label,
                score = category.score,
                boundingBox = detection.boundingBox,
                isPerson = isPerson
            )
        }
    }
}
