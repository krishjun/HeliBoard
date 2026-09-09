// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.aiswipe

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.ArrayDeque

fun interface AiSwipeProvider {
    suspend fun predict(request: AiSwipeRequest): AiSwipeResult?
}

enum class AiSwipeStatus { WORKING, UNAVAILABLE, RATE_LIMITED, READY }

/** Main-thread confined. Local decoding is independent and is never awaited by this engine. */
class AiSwipeEngine(
    private val scope: CoroutineScope,
    private val provider: AiSwipeProvider,
    private val now: () -> Long,
) {
    private var revision = 0L
    private var job: Job? = null
    private var lastRequest: Long? = null
    private var retryAfter = 0L
    private val starts = ArrayDeque<Long>()
    private val singleFlight = Mutex()

    fun invalidate() {
        revision++
        job?.cancel()
        job = null
    }

    fun submit(request: AiSwipeRequest, valid: () -> Boolean,
               status: (AiSwipeStatus) -> Unit, result: (AiSwipeResult) -> Unit) {
        invalidate()
        val id = revision
        job = scope.launch {
            delay(DEBOUNCE_MS)
            singleFlight.withLock {
                fun current() = id == revision && valid() && isActive
                if (!current()) return@withLock
                val delayMs = lastRequest?.let { MIN_INTERVAL_MS - (now() - it) } ?: 0L
                if (delayMs > 0) delay(delayMs)
                if (!current()) return@withLock
                val time = now()
                while (starts.isNotEmpty() && time - starts.first >= 60_000) starts.removeFirst()
                if (time < retryAfter || starts.size >= REQUESTS_PER_MINUTE) {
                    status(AiSwipeStatus.RATE_LIMITED)
                    return@withLock
                }
                starts.addLast(time)
                lastRequest = time
                status(AiSwipeStatus.WORKING)
                val prediction = try {
                    withTimeoutOrNull(if (request.trace == null) TIMEOUT_MS else SENTENCE_TIMEOUT_MS) { provider.predict(request) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Do not log SDK exceptions: they can include input or response content.
                    null
                }
                if (!current()) return@withLock
                if (prediction == null) {
                    retryAfter = now() + FAILURE_COOLDOWN_MS
                    status(AiSwipeStatus.UNAVAILABLE)
                } else {
                    status(AiSwipeStatus.READY)
                    result(prediction)
                }
            }
        }
    }

    companion object {
        const val DEBOUNCE_MS = 250L
        const val MIN_INTERVAL_MS = 1000L
        const val TIMEOUT_MS = 3000L
        const val SENTENCE_TIMEOUT_MS = 8000L
        const val FAILURE_COOLDOWN_MS = 10_000L
        const val REQUESTS_PER_MINUTE = 30
    }
}
