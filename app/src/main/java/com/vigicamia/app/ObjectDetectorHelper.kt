package com.vigicamia.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.tasks.await

data class DetectionResult(
    val categoryEs: String,
    val rawLabel: String,
    val score: Float,
    val boundingBox: RectF,
    val isPerson: Boolean
)

class ObjectDetectorHelper(private val context: Context) {
    private var detector: ObjectDetector? = null

    fun initialize(): String {
        return try {
            val options = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build()
            detector = ObjectDetection.getClient(options)
            "OK"
        } catch (e: Exception) {
            e.message ?: "Error iniciando ML Kit"
        }
    }

    suspend fun detect(bitmap: Bitmap): List<DetectionResult> {
        val currentDetector = detector ?: return emptyList()
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val objects = currentDetector.process(image).await()

            objects.map { obj ->
                val topLabel = obj.labels.maxByOrNull { it.confidence }
                val labelText = topLabel?.text ?: "Objeto"
                val confidence = topLabel?.confidence ?: 0.75f

                // ML Kit clasifica: Fashion good (ropa/personas), Home good, Food, Place, Plant
                val isPerson = labelText.equals("Fashion good", ignoreCase = true) ||
                        labelText.equals("Person", ignoreCase = true)

                val categoryEs = when {
                    isPerson -> "PERSONA / INTRUSO"
                    labelText.equals("Home good", ignoreCase = true) -> "OBJETO"
                    labelText.equals("Place", ignoreCase = true) -> "VEHÍCULO / ESTRUCTURA"
                    labelText.equals("Plant", ignoreCase = true) -> "ANIMAL / PLANTA"
                    else -> "OBJETO ($labelText)"
                }

                DetectionResult(
                    categoryEs = categoryEs,
                    rawLabel = labelText,
                    score = confidence,
                    boundingBox = RectF(obj.boundingBox),
                    isPerson = isPerson
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
