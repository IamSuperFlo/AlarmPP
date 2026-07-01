package com.example.photoalarm

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.classifier.ImageClassifier

/**
 * On-device object recognition using a bundled TensorFlow Lite MobileNet model.
 * Runs fully offline — no internet, no API key, no usage limits.
 *
 * The model recognises ~1000 everyday ImageNet categories. We only use a curated
 * subset (see [TARGETS]) whose labels the model knows well, so the alarm can ask
 * for a realistic household object.
 */
class ObjectClassifier(context: Context) {

    private val classifier: ImageClassifier

    init {
        val options = ImageClassifier.ImageClassifierOptions.builder()
            .setMaxResults(5)
            .setScoreThreshold(0.10f)
            .build()
        // "model.tflite" is downloaded into assets at build time (see download_model.gradle).
        classifier = ImageClassifier.createFromFileAndOptions(context, "model.tflite", options)
    }

    /**
     * Returns true if [bitmap] contains the requested [target] object.
     * Also returns the best label seen, for user feedback.
     */
    fun matches(bitmap: Bitmap, target: Target): Result {
        val image = TensorImage.fromBitmap(bitmap)
        val classifications = classifier.classify(image)
        val categories = classifications.flatMap { it.categories }
            .sortedByDescending { it.score }

        for (cat in categories) {
            val label = cat.label.lowercase()
            if (target.keywords.any { label.contains(it) }) {
                return Result(true, cat.label, cat.score)
            }
        }
        val best = categories.firstOrNull()
        return Result(false, best?.label ?: "necunoscut", best?.score ?: 0f)
    }

    fun close() {
        classifier.close()
    }

    data class Result(val matched: Boolean, val bestLabel: String, val bestScore: Float)

    /** A requestable object: Romanian display name + English label keywords the model uses. */
    data class Target(val displayRo: String, val keywords: List<String>)

    companion object {
        val TARGETS = listOf(
            Target("o BANANĂ", listOf("banana")),
            Target("o CANĂ", listOf("coffee mug", "cup", "mug", "espresso", "coffeepot")),
            Target("o STICLĂ", listOf("water bottle", "bottle", "pop bottle", "wine bottle", "beer bottle")),
            Target("un TELEFON", listOf("cellular telephone", "cellphone", "cell phone", "telephone", "ipod", "hand-held computer")),
            Target("o TELECOMANDĂ", listOf("remote control", "remote")),
            Target("un PANTOF", listOf("running shoe", "sneaker", "loafer", "sandal", "clog", "cowboy boot")),
            Target("un RUCSAC", listOf("backpack", "back pack", "knapsack", "purse", "mailbag")),
            Target("OCHELARI de soare", listOf("sunglass", "sunglasses", "dark glasses")),
            Target("o PERNĂ", listOf("pillow", "cushion")),
            Target("o TASTATURĂ", listOf("computer keyboard", "keyboard", "typewriter keyboard", "space bar")),
            Target("un CEAS", listOf("wall clock", "analog clock", "digital clock", "clock")),
            Target("un LAPTOP", listOf("laptop", "notebook", "computer"))
        )

        fun randomTarget(): Target = TARGETS.random()

        /** Picks [n] distinct random targets (for difficulty levels). */
        fun randomTargets(n: Int): List<Target> =
            TARGETS.shuffled().take(n.coerceIn(1, TARGETS.size))
    }
}
