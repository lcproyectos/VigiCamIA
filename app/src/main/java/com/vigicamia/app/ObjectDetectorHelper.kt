package com.vigicamia.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector

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

    fun initialize(): String {
        return try {
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setMaxResults(5)
                .setScoreThreshold(0.40f)
                .build()
            detector = ObjectDetector.createFromFileAndOptions(
                context,
                "efficientdet_lite0.tflite",
                options
            )
            "OK"
        } catch (e: Exception) {
            e.message ?: "Error cargando modelo IA"
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
