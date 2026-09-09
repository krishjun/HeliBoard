// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.aiswipe

import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import kotlin.math.hypot

/** Exclusive owner from DOWN to UP. Never hands half a cancelled gesture to the native decoder. */
class AiSwipeSentenceTouch(
    private val view: MainKeyboardView,
    private val enabled: () -> Boolean,
    private val begin: (Keyboard) -> Boolean,
    private val complete: (List<AiSwipeTraceKey>, List<AiSwipeTracePoint>) -> Unit,
    private val failed: (Boolean) -> Unit,
) : View.OnTouchListener {
    private var owns = false
    private var discarded = false
    private var keyboard: Keyboard? = null
    private var down: MotionEvent? = null
    private var pointer = -1
    private var keys = emptyList<AiSwipeTraceKey>()
    private val points = ArrayList<AiSwipeTracePoint>()
    private var width = 1f
    private var height = 1f
    private var distance = 0f
    private val trail = Trail()
    private val expire = Runnable { if (owns && !discarded) reject(true) }

    init { view.setOnTouchListener(this) }

    fun cancel() {
        if (owns) discarded = true
        view.removeCallbacks(expire)
        down?.recycle(); down = null
        points.clear(); keys = emptyList(); keyboard = null
        view.overlay.remove(trail)
    }

    fun close() { cancel(); view.setOnTouchListener(null) }

    private fun reject(tooLong: Boolean) { cancel(); failed(tooLong) }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            cancel(); owns = false; discarded = false
            if (!enabled()) return false
            val layout = view.keyboard ?: return false
            width = layout.mMostCommonKeyWidth.toFloat().coerceAtLeast(1f)
            height = layout.mMostCommonKeyHeight.toFloat().coerceAtLeast(1f)
            val x = event.x - view.paddingLeft; val y = event.y - view.paddingTop
            val start = layout.sortedKeys.firstOrNull {
                x >= it.x && x < it.x + it.width && y >= it.y && y < it.y + it.height
            } ?: return false
            if (!Character.isLetter(start.code) || start.code > 0xffff) return false
            val mapped = runCatching { layout.sortedKeys.filter { it.code == 32 || (it.code <= 0xffff && Character.isLetter(it.code)) }
                .map { AiSwipeTraceKey(it.code.toChar().toString(), (it.x + it.width / 2f) / width,
                    (it.y + it.height / 2f) / height, it.width / width, it.height / height) } }.getOrElse { return false }
            if (mapped.size !in 2..AiSwipeTrace.MAX_KEYS) return false
            // No text is read by the adapter. The controller gates and snapshots the current field.
            val accepted = begin(layout)
            owns = true
            if (!accepted) { discarded = true; return true }
            keyboard = layout; keys = mapped; pointer = event.getPointerId(0)
            down = MotionEvent.obtain(event); distance = 0f
            append(event.x, event.y, event.eventTime)
            trail.setBounds(0, 0, view.width, view.height)
            view.overlay.add(trail)
            view.postDelayed(expire, 60_000)
            return true
        }
        if (!owns) return false
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            reject(false); owns = false; return true
        }
        if (!discarded && (event.pointerCount != 1 || event.getPointerId(0) != pointer ||
                keyboard !== view.keyboard || !enabled())) reject(false)
        if (!discarded && (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_UP)) {
            for (i in 0 until event.historySize) {
                if (!append(event.getHistoricalX(0, i), event.getHistoricalY(0, i), event.getHistoricalEventTime(i))) break
            }
            if (!discarded) append(event.x, event.y, event.eventTime, event.actionMasked == MotionEvent.ACTION_UP)
            trail.invalidateSelf()
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            if (!discarded) {
                if (distance < 0.45f) {
                    // Preserve normal taps without letting native word decoding see a sentence stroke.
                    val first = down?.let { MotionEvent.obtain(it) }
                    cancel(); owns = false
                    if (first != null) {
                        try { view.processMotionEvent(first); view.processMotionEvent(event) }
                        finally { first.recycle() }
                    }
                    return true
                }
                val capturedKeys = keys.toList(); val capturedPoints = points.toList()
                cancel(); owns = false
                if (capturedPoints.size >= 2) complete(capturedKeys, capturedPoints) else failed(false)
                return true
            }
            cancel(); owns = false
        }
        return true
    }

    private fun append(x: Float, y: Float, time: Long, force: Boolean = false): Boolean {
        val start = down ?: return false
        val elapsed = time - start.eventTime
        if (elapsed < 0) return true // duplicate historical sample from before DOWN
        if (elapsed > 60_000 || points.size >= 4096) { reject(true); return false }
        val nx = (x - view.paddingLeft) / width; val ny = (y - view.paddingTop) / height
        val layout = keyboard ?: return false
        if (!nx.isFinite() || !ny.isFinite() || nx !in 0f..(layout.mOccupiedWidth / width) ||
            ny !in 0f..(layout.mOccupiedHeight / height) || nx > 40f || ny > 40f) {
            reject(false); return false
        }
        val previous = points.lastOrNull()
        if (previous != null) {
            if (elapsed < previous.time) return true
            val delta = hypot(nx - previous.x, ny - previous.y)
            // Retain pauses and turns but avoid oversampling stationary fingers.
            if (!force && delta < 0.04f && elapsed - previous.time < 80) return true
            distance += delta
        }
        points.add(AiSwipeTracePoint(nx, ny, elapsed.toInt()))
        return true
    }

    private inner class Trail : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        override fun draw(canvas: Canvas) {
            if (points.size < 2) return
            paint.color = Settings.getValues().mColors.get(ColorType.KEY_TEXT)
            paint.alpha = 160; paint.strokeWidth = width * 0.08f
            val path = Path()
            points.takeLast(48).forEachIndexed { index, p ->
                val x = p.x * width + view.paddingLeft; val y = p.y * height + view.paddingTop
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
        @Deprecated("Drawable opacity") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
