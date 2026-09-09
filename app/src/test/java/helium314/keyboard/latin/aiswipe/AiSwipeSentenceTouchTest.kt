// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.aiswipe

import android.view.MotionEvent
import android.view.ViewOverlay
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.MainKeyboardView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner

/** Actual MotionEvent path: no local word decoder or network callback before the final UP. */
@RunWith(RobolectricTestRunner::class)
class AiSwipeSentenceTouchTest {
    private val view = mock(MainKeyboardView::class.java)
    private val keyboard = mock(Keyboard::class.java)
    private var enabled = true
    private var completions = 0
    private var failures = 0
    private var limit = false
    private var captured = emptyList<AiSwipeTracePoint>()
    private val touch: AiSwipeSentenceTouch
    init {
        for ((field, value) in mapOf("mMostCommonKeyWidth" to 100, "mMostCommonKeyHeight" to 100,
            "mOccupiedWidth" to 300, "mOccupiedHeight" to 100)) {
            Keyboard::class.java.getField(field).apply { isAccessible = true }.setInt(keyboard, value)
        }
        val keys = "abc".mapIndexed { index, ch -> mock(Key::class.java).also {
            `when`(it.code).thenReturn(ch.code); `when`(it.x).thenReturn(index * 100)
            `when`(it.y).thenReturn(0); `when`(it.width).thenReturn(100); `when`(it.height).thenReturn(100)
        } }
        `when`(keyboard.sortedKeys).thenReturn(keys)
        `when`(view.keyboard).thenReturn(keyboard)
        `when`(view.overlay).thenReturn(mock(ViewOverlay::class.java))
        touch = AiSwipeSentenceTouch(view, { enabled }, { true }, { _, points ->
            completions++; captured = points
        }, { tooLong -> failures++; limit = tooLong })
    }
    private fun event(action: Int, time: Long, x: Float, y: Float = 50f): Boolean {
        val event = MotionEvent.obtain(100, time, action, x, y, 0)
        return try { touch.onTouch(view, event) } finally { event.recycle() }
    }

    @Test fun continuousMotionProducesExactlyOneFullTraceAfterRelease() {
        assertTrue(event(MotionEvent.ACTION_DOWN, 100, 50f))
        assertTrue(event(MotionEvent.ACTION_MOVE, 200, 150f))
        assertTrue(event(MotionEvent.ACTION_MOVE, 300, 250f))
        assertEquals(0, completions)
        assertTrue(event(MotionEvent.ACTION_UP, 400, 250f))
        assertEquals(1, completions)
        assertEquals(0.5f, captured.first().x)
        assertEquals(2.5f, captured.last().x)
        assertEquals(300, captured.last().time)
        verify(view, never()).processMotionEvent(any(MotionEvent::class.java))
    }

    @Test fun cancellationConsumesRemainingEventsWithoutCommittingOrNativeFallback() {
        event(MotionEvent.ACTION_DOWN, 100, 50f)
        event(MotionEvent.ACTION_MOVE, 200, 250f)
        touch.cancel()
        assertTrue(event(MotionEvent.ACTION_MOVE, 300, 150f))
        assertTrue(event(MotionEvent.ACTION_UP, 400, 250f))
        assertEquals(0, completions)
        verify(view, never()).processMotionEvent(any(MotionEvent::class.java))
    }

    @Test fun permissionLossDuringGestureDiscardsWholeStroke() {
        event(MotionEvent.ACTION_DOWN, 100, 50f)
        enabled = false
        event(MotionEvent.ACTION_MOVE, 200, 150f)
        event(MotionEvent.ACTION_UP, 300, 250f)
        assertEquals(0, completions)
        assertEquals(1, failures)
        verify(view, never()).processMotionEvent(any(MotionEvent::class.java))
    }

    @Test fun longGestureIsRejectedInsteadOfSilentlyTruncated() {
        event(MotionEvent.ACTION_DOWN, 100, 50f)
        event(MotionEvent.ACTION_MOVE, 200, 150f)
        event(MotionEvent.ACTION_UP, 60_101, 250f)
        assertEquals(0, completions)
        assertTrue(limit)
    }

    @Test fun leavingKeyboardAndSystemCancellationDoNotInsertAnyText() {
        event(MotionEvent.ACTION_DOWN, 100, 50f)
        event(MotionEvent.ACTION_MOVE, 200, 350f)
        event(MotionEvent.ACTION_UP, 300, 250f)
        assertEquals(0, completions)
        event(MotionEvent.ACTION_DOWN, 100, 50f)
        event(MotionEvent.ACTION_CANCEL, 200, 150f)
        assertEquals(0, completions)
        verify(view, never()).processMotionEvent(any(MotionEvent::class.java))
    }

    @Test fun ordinaryTapIsReplayedThroughNativeInput() {
        event(MotionEvent.ACTION_DOWN, 100, 50f)
        event(MotionEvent.ACTION_UP, 200, 51f)
        assertEquals(0, completions)
        verify(view, times(2)).processMotionEvent(any(MotionEvent::class.java))
    }

    @Test fun disabledSentenceModeDoesNotInterceptKeyboardEvents() {
        enabled = false
        assertFalse(event(MotionEvent.ACTION_DOWN, 100, 50f))
        assertFalse(event(MotionEvent.ACTION_UP, 200, 150f))
    }
}
