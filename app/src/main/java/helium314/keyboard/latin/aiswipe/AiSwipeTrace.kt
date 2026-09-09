// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.aiswipe

import kotlinx.serialization.json.*
import java.util.Locale
import kotlin.math.*

/** Coordinates are relative to keyboard key width/height, never screen coordinates. */
data class AiSwipeTraceKey(val label: String, val x: Float, val y: Float, val width: Float, val height: Float) {
    init {
        require(label == " " || AiSwipeRequest.isSwipeWord(label))
        require(label.length <= 4 && listOf(x, y, width, height).all { it.isFinite() })
        require(x in -1f..40f && y in -1f..40f && width in 0.01f..40f && height in 0.01f..40f)
    }
    fun distance(p: AiSwipeTracePoint): Float = hypot(p.x - x, p.y - y)
    fun contains(p: AiSwipeTracePoint) = abs(p.x - x) <= width / 2 && abs(p.y - y) <= height / 2
}

data class AiSwipeTracePoint(val x: Float, val y: Float, val time: Int) {
    init { require(x.isFinite() && y.isFinite() && x in -2f..42f && y in -2f..42f && time in 0..60_000) }
}

/** Bounded, immutable evidence for ONE continuous finger-down sentence, not native word candidates. */
data class AiSwipeTrace(val keys: List<AiSwipeTraceKey>, val points: List<AiSwipeTracePoint>) {
    init {
        require(keys.size in 2..MAX_KEYS && keys.any { it.label != " " })
        require(points.size in 2..MAX_POINTS)
        require(points.zipWithNext().all { (a, b) -> a.time <= b.time })
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("coordinate_units", "100 = one typical key width or height; time in milliseconds")
        put("keys_label_x_y_width_height", JsonArray(keys.map { k -> buildJsonArray {
            add(k.label); add((k.x * 100).roundToInt()); add((k.y * 100).roundToInt())
            add((k.width * 100).roundToInt()); add((k.height * 100).roundToInt())
        } }))
        put("path_x_y_time", JsonArray(points.map { p -> buildJsonArray {
            add((p.x * 100).roundToInt()); add((p.y * 100).roundToInt()); add(p.time)
        } }))
        // This is deliberately not described as recognized letters: crossing a key is not typing it.
        val visits = mutableListOf<String>()
        for (p in points) {
            val space = keys.firstOrNull { it.label == " " && it.contains(p) }
            val nearest = space ?: keys.filter { it.label != " " }.minByOrNull { it.distance(p) }!!
            val label = nearest.label.lowercase(Locale.ROOT)
            if (visits.lastOrNull() != label) visits.add(label)
        }
        put("nearest_key_visits_not_literal_text", JsonArray(visits.map(::JsonPrimitive)))
    }

    /** Conservative monotone letter/path alignment. A plausibility filter, not a trained decoder. */
    fun supports(text: String): Boolean {
        val letters = text.lowercase(Locale.ROOT).filter(Char::isLetter)
        if (letters.isEmpty() || letters.length > MAX_TEXT) return false
        val byLabel = keys.filter { it.label != " " }.groupBy { it.label.lowercase(Locale.ROOT) }
        // Only single-code-point alphabetic layouts are offered by the touch adapter in this version.
        val targets = letters.map { byLabel[it.toString()] ?: return false }
        if (targets.first().minOf { it.distance(points.first()) } > 1.6f ||
            targets.last().minOf { it.distance(points.last()) } > 1.6f) return false
        // Every new letter must move forward on the path. Adjacent repeats may share a sample.
        // Transition samples may be skipped (e.g. crossing D and F while going from A to G).
        var previous = FloatArray(points.size) { Float.POSITIVE_INFINITY }
        for ((i, choices) in targets.withIndex()) {
            val current = FloatArray(points.size) { Float.POSITIVE_INFINITY }
            var prefix = Float.POSITIVE_INFINITY
            for (j in points.indices) {
                val sameLetter = i > 0 && letters[i] == letters[i - 1]
                if (j > 0) prefix = min(prefix, previous[j - 1])
                val base = when {
                    i == 0 && j <= 2 -> 0f
                    i == 0 -> Float.POSITIVE_INFINITY
                    sameLetter -> min(prefix, previous[j])
                    else -> prefix
                }
                val distance = choices.minOf { it.distance(points[j]) }
                if (distance <= 1.6f) current[j] = base + distance * distance
            }
            previous = current
        }
        val score = previous.takeLast(3).minOrNull() ?: return false
        return score / letters.length <= 0.85f
    }

    companion object {
        const val MAX_POINTS = 384
        const val MAX_KEYS = 80
        const val MAX_TEXT = 320
        const val MAX_ALTERNATIVES = 3

        /** Keep endpoints and the most significant bends/pauses over the ENTIRE gesture. */
        fun simplify(points: List<AiSwipeTracePoint>): List<AiSwipeTracePoint> {
            if (points.size <= MAX_POINTS) return points.toList()
            val retained = points.toMutableList()
            // Bounded input (<=4096). Removing low-error points retains turns and dwell endpoints.
            while (retained.size > MAX_POINTS) {
                var best = 1
                var error = Float.POSITIVE_INFINITY
                for (i in 1 until retained.lastIndex) {
                    val a = retained[i - 1]; val b = retained[i]; val c = retained[i + 1]
                    val duration = c.time - a.time
                    val fraction = if (duration > 0) (b.time - a.time).toFloat() / duration else 0.5f
                    val cost = hypot(b.x - a.x - fraction * (c.x - a.x),
                        b.y - a.y - fraction * (c.y - a.y))
                    if (cost < error) { error = cost; best = i }
                }
                retained.removeAt(best)
            }
            return retained.toList()
        }

        fun validSentence(text: String): Boolean = text.length in 1..MAX_TEXT && text == text.trim() &&
            text.any(Char::isLetter) && text.split(Regex("\\s+")).size <= 48 && text.all {
                it.isLetter() || Character.getType(it) == Character.NON_SPACING_MARK.toInt() ||
                    it in " '’\".,!?;:()-"
            }

        fun insertion(prefix: String, sentence: String, locale: String): String {
            val noSpace = locale.substringBefore('-') in setOf("zh", "ja", "th", "lo", "km", "my")
            val needsSpace = !noSpace && prefix.isNotEmpty() && !prefix.last().isWhitespace() &&
                prefix.last() !in "([{\"“" && sentence.firstOrNull() !in listOf('.', ',', '!', '?', ';', ':')
            return (if (needsSpace) " " else "") + sentence
        }
    }
}
