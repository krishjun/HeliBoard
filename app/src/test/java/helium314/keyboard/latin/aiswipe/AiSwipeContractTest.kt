// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.aiswipe

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test

class AiSwipeContractTest {
    private val request = AiSwipeRequest("See you ", listOf("soon", "son"), "en-US", true)

    @Test fun parsesOnlySupportedCandidate() {
        assertEquals(AiSwipeResult("soon", "at the usual place."),
            AiSwipeResult.parse("""{"word":"soon","continuation":"at the usual place."}""", request))
        assertNull(AiSwipeResult.parse("""{"word":"tomorrow","continuation":""}""", request))
    }
    @Test fun malformedResponsesFailClosed() {
        listOf(null, "", "```json\n{}\n```", "{}", "[]", "true", "x".repeat(2049),
            """{"word":"soon","continuation":true}""", """{"word":42,"continuation":""}""")
            .forEach { assertNull(AiSwipeResult.parse(it, request)) }
    }
    @Test fun controlsAndLongResponsesFailClosed() {
        listOf("\\n", "\\t", "\\u202e", "\\u2066", "x".repeat(121), "a ".repeat(17))
            .forEach { assertNull(AiSwipeResult.parse("""{"word":"soon","continuation":"$it"}""", request)) }
    }
    @Test fun continuationSwitchCannotBeOverriddenByModel() {
        assertEquals("", AiSwipeResult.parse("""{"word":"son","continuation":"hello"}""",
            request.copy(completeSentence = false))!!.continuation)
    }
    @Test fun joiningDoesNotDuplicatePunctuationSpacing() {
        assertEquals("soon enough", AiSwipeResult("soon", "enough").joined("en"))
        assertEquals("soon!", AiSwipeResult("soon", "!").joined("en"))
        assertEquals("你好朋友", AiSwipeResult("你好", "朋友").joined("zh-CN"))
        assertEquals("soon", AiSwipeResult("soon", "").joined("en"))
    }
    @Test fun boundedContextNeverStartsWithHalfAnEmoji() {
        val clipped = AiSwipeRequest.contextTail("\uD83D\uDE00" + "a".repeat(255))
        assertEquals(255, clipped.length)
        assertFalse(clipped.first().isLowSurrogate())
    }
    @Test fun candidateValidationRejectsInstructionsAndSensitiveTokens() {
        listOf("", "hello world", "a@b", "1234", "x".repeat(49), "\\n").forEach {
            assertFalse(AiSwipeRequest.isSwipeWord(it))
        }
        listOf("hello", "can't", "école", "नमस्ते", "你好").forEach {
            assertTrue(AiSwipeRequest.isSwipeWord(it))
        }
    }
    @Test fun stampRequiresSameSessionRevisionCursorTextAndEndOfField() {
        val stamp = AiSwipeEditStamp(1, 2, 3, "abc")
        assertTrue(stamp.matches(1, 2, 3, 3, "abc", ""))
        assertFalse(stamp.matches(2, 2, 3, 3, "abc", ""))
        assertFalse(stamp.matches(1, 3, 3, 3, "abc", ""))
        assertFalse(stamp.matches(1, 2, 2, 3, "abc", ""))
        assertFalse(stamp.matches(1, 2, 3, 3, "abx", ""))
        assertFalse(stamp.matches(1, 2, 3, 3, "abc", "x"))
        assertFalse(AiSwipeEditStamp(1, 2, -1, "").matches(1, 2, -1, -1, "", ""))
    }
    private fun allowed(type: Int, options: Int = 0, consent: Boolean = true,
                        incognito: Boolean = false, locked: Boolean = false) =
        AiSwipePolicy.allowsField(type, options, consent, incognito, locked)

    @Test fun sensitiveEditorTypesFailClosed() {
        listOf(InputType.TYPE_NULL, InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        ).forEach { assertFalse("Input type $it must be blocked", allowed(it)) }
    }
    @Test fun consentIncognitoLockAndNoLearningAreMandatoryGates() {
        val text = InputType.TYPE_CLASS_TEXT
        assertTrue(allowed(text))
        assertFalse(allowed(text, consent = false))
        assertFalse(allowed(text, incognito = true))
        assertFalse(allowed(text, locked = true))
        assertFalse(allowed(text, options = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
    }
    @Test fun contextRedFlagsSuppressRequests() {
        listOf("my code is 1234", "contact me a@example.com", "open https://example.org",
            "abcdefghijklmnopqrstuvwxyz0123456789").forEach { assertFalse(AiSwipePolicy.allowsContext(it)) }
        assertTrue(AiSwipePolicy.allowsContext("I will see you "))
    }
}
