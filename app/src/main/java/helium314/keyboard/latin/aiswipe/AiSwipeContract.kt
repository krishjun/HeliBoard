// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.aiswipe

import kotlinx.serialization.json.*

/** Short-lived data only: never persist or log requests/results. */
data class AiSwipeRequest(
    val context: String,
    val candidates: List<String>,
    val locale: String,
    val completeSentence: Boolean,
) {
    init {
        require(context.length <= MAX_CONTEXT)
        require(candidates.size in 1..MAX_CANDIDATES)
        require(candidates.all(::isSwipeWord))
        require(locale.length <= 64)
    }

    fun toJson(): String = buildJsonObject {
        put("draft_before_word", context)
        put("swipe_candidates", JsonArray(candidates.map(::JsonPrimitive)))
        put("keyboard_locale", locale)
        put("offer_continuation", completeSentence)
    }.toString()

    companion object {
        const val MAX_CONTEXT = 256
        const val MAX_CANDIDATES = 8
        const val MAX_WORD = 48
        const val MAX_CONTINUATION = 120

        fun isSwipeWord(text: String): Boolean = text.length in 1..MAX_WORD &&
            text.any(Char::isLetter) && text.all {
                it.isLetter() || Character.getType(it) == Character.NON_SPACING_MARK.toInt() ||
                    Character.getType(it) == Character.COMBINING_SPACING_MARK.toInt() ||
                    it == '\'' || it == '’' || it == '-'
            }

        /** Avoid cutting a UTF-16 surrogate pair when clipping a draft. */
        fun contextTail(text: String): String {
            val tail = text.takeLast(MAX_CONTEXT)
            return if (tail.firstOrNull()?.isLowSurrogate() == true) tail.drop(1) else tail
        }
    }
}

data class AiSwipeResult(val word: String, val continuation: String) {
    companion object {
        /** Schema is not a security boundary: validate again before displaying/inserting. */
        fun parse(raw: String?, request: AiSwipeRequest): AiSwipeResult? {
            if (raw == null || raw.length > 2048) return null
            val obj = try { Json.parseToJsonElement(raw) as? JsonObject } catch (_: Exception) { null }
                ?: return null
            fun string(key: String): String? = (obj[key] as? JsonPrimitive)
                ?.takeIf { it.isString }?.content
            val word = string("word") ?: return null
            // The model reranks real local decoder candidates; it cannot invent a replacement.
            if (word !in request.candidates) return null
            val tail = string("continuation") ?: return null
            if (tail.length > AiSwipeRequest.MAX_CONTINUATION || tail.any {
                    it.isISOControl() || it == '\u2028' || it == '\u2029' ||
                        it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069'
                }) return null
            val continuation = if (request.completeSentence) tail.trim() else ""
            if (continuation.split(Regex("\\s+")).size > 16) return null
            return AiSwipeResult(word, continuation)
        }
    }
}

/** Captures edit identity as well as content: equal text in another field is not the same edit. */
data class AiSwipeEditStamp(val session: Long, val revision: Long, val cursor: Int, val before: String) {
    fun matches(session: Long, revision: Long, start: Int, end: Int, before: String, after: String): Boolean =
        this.session == session && this.revision == revision && cursor >= 0 &&
            cursor == start && start == end && this.before == before && after.isEmpty()
}

const val AI_SWIPE_ENABLED = "ai_swipe_enabled"
const val AI_SWIPE_SENTENCES = "ai_swipe_sentences"
