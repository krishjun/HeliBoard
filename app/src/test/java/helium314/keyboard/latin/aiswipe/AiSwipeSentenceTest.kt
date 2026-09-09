// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.aiswipe

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AiSwipeSentenceTest {
    private fun keys(rows: List<String> = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")) =
        rows.flatMapIndexed { y, row -> row.mapIndexed { x, ch ->
            AiSwipeTraceKey(ch.toString(), x + 0.5f + y * 0.25f, y + 0.5f, 0.9f, 0.9f)
        } } + AiSwipeTraceKey(" ", 5f, 3.5f, 5f, 0.9f)

    private fun trace(text: String, layout: List<AiSwipeTraceKey> = keys()): AiSwipeTrace {
        val points = mutableListOf<AiSwipeTracePoint>()
        val letters = text.lowercase().filter(Char::isLetter)
        letters.forEach { ch ->
            val key = layout.first { it.label == ch.toString() }
            val previous = points.lastOrNull()
            if (previous != null) for (step in 1..3) {
                val t = step / 4f
                points.add(AiSwipeTracePoint(previous.x + (key.x - previous.x) * t,
                    previous.y + (key.y - previous.y) * t, points.size * 30))
            }
            points.add(AiSwipeTracePoint(key.x, key.y, points.size * 30))
        }
        return AiSwipeTrace(layout, points)
    }

    @Test fun oneContinuousTraceSupportsWholeSentenceWithoutWordCandidatesOrSpaces() {
        val trace = trace("I will see you soon")
        assertTrue(trace.supports("I will see you soon."))
        assertFalse(trace.supports("Buy a zebra."))
        val request = AiSwipeRequest("", emptyList(), "en", false, trace)
        val payload = Json.parseToJsonElement(request.toJson()).jsonObject
        assertEquals("decode_continuous_sentence", payload["task"]?.jsonPrimitive?.content)
        assertTrue(payload["swipe_candidates"]!!.jsonArray.isEmpty())
        assertTrue(payload["gesture"]!!.jsonObject["path_x_y_time"]!!.jsonArray.size > 20)
    }

    @Test fun ambiguousBoundariesProduceMultipleSelectableVersions() {
        val request = AiSwipeRequest("", emptyList(), "en", false, trace("nowhere"))
        val result = AiSwipeResult.parse("""{"alternatives":["Now here.","Nowhere.","NOW HERE."]}""", request)
        assertEquals(listOf("Now here.", "Nowhere."), result?.alternatives)
    }

    @Test fun unrelatedModelTextDoesNotBecomeAKeyboardInsertion() {
        val request = AiSwipeRequest("", emptyList(), "en", false, trace("hello"))
        assertTrue(AiSwipeResult.parse("""{"alternatives":["A zebra lives here."]}""", request)!!.alternatives.isEmpty())
    }

    @Test fun strictSentenceResponseParsingRejectsMalformedAndUnsafeOutputs() {
        val request = AiSwipeRequest("", emptyList(), "en", false, trace("hello"))
        for (raw in listOf("{}", "[]", "null", "{", """{"alternatives":[7]}""",
            """{"alternatives":["hello","hello","hello","hello"]}""",
            """{"alternatives":["hello\nthere"]}""", """{"alternatives":["hello\u202ethere"]}""",
            """{"alternatives":["hello 1234"]}""", """{"alternatives":[" hello "]}"""))
            assertNull(raw, AiSwipeResult.parse(raw, request))
        assertEquals(emptyList<String>(), AiSwipeResult.parse("""{"alternatives":[]}""", request)?.alternatives)
    }

    @Test fun pathUsesActualNonQwertyLayoutAndSupportsRepeatedLetters() {
        val layout = keys(listOf("azertyuiop", "qsdfghjklm", "wxcvbn"))
        assertTrue(trace("meet me", layout).supports("Meet me."))
    }

    @Test fun reverseLetterOrderDoesNotMatchTheSamePath() {
        val trace = trace("azp")
        assertTrue(trace.supports("A z p"))
        assertFalse(trace.supports("P z a"))
    }

    @Test fun boundedSimplificationPreservesBeginningEndingTurnAndWholeDuration() {
        val points = (0..1600).map { i ->
            val x = if (i <= 800) i / 100f else (1600 - i) / 100f
            AiSwipeTracePoint(x, 1f, i * 20)
        }
        val result = AiSwipeTrace.simplify(points)
        assertEquals(AiSwipeTrace.MAX_POINTS, result.size)
        assertEquals(points.first(), result.first())
        assertEquals(points.last(), result.last())
        assertTrue(result.any { it.x == 8f })
        assertTrue(result.zipWithNext().all { (a, b) -> a.time <= b.time })
    }

    @Test fun rawPathAndKeyboardBoundsRejectInvalidData() {
        assertThrows(IllegalArgumentException::class.java) { AiSwipeTracePoint(Float.NaN, 1f, 0) }
        assertThrows(IllegalArgumentException::class.java) { AiSwipeTracePoint(1f, 1f, 60_001) }
        assertThrows(IllegalArgumentException::class.java) { AiSwipeTrace(keys(), listOf(AiSwipeTracePoint(1f, 1f, 0))) }
        assertThrows(IllegalArgumentException::class.java) { AiSwipeTrace(keys(), listOf(
            AiSwipeTracePoint(1f, 1f, 100), AiSwipeTracePoint(2f, 1f, 10))) }
    }

    @Test fun sentenceInsertionAddsOnlyTheNeededBoundarySpace() {
        assertEquals("Hello.", AiSwipeTrace.insertion("", "Hello.", "en"))
        assertEquals(" Hello.", AiSwipeTrace.insertion("Previous.", "Hello.", "en"))
        assertEquals("Hello.", AiSwipeTrace.insertion("Previous. ", "Hello.", "en"))
        assertEquals("Hello.", AiSwipeTrace.insertion("(", "Hello.", "en"))
        assertEquals("你好", AiSwipeTrace.insertion("你好", "你好", "zh-CN"))
    }
}
