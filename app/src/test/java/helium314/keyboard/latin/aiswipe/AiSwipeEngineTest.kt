// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.aiswipe

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiSwipeEngineTest {
    private val request = AiSwipeRequest("", listOf("hello"), "en", true)
    private val prediction = AiSwipeResult("hello", "there")

    @Test fun debounceAndCancellationDoNotCallCloud() = runTest {
        var calls = 0
        val engine = AiSwipeEngine(this, AiSwipeProvider { calls++; prediction }, { testScheduler.currentTime })
        engine.submit(request, { true }, {}, { fail("Cancelled result") })
        advanceTimeBy(249)
        assertEquals(0, calls)
        engine.invalidate()
        advanceUntilIdle()
        assertEquals(0, calls)
    }
    @Test fun onlyMostRecentSwipeProducesResult() = runTest {
        val results = mutableListOf<AiSwipeResult>()
        var calls = 0
        val engine = AiSwipeEngine(this, AiSwipeProvider { calls++; prediction }, { testScheduler.currentTime })
        engine.submit(request, { true }, {}, results::add)
        advanceTimeBy(100)
        engine.submit(request, { true }, {}, results::add)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(listOf(prediction), results)
    }
    @Test fun editorInvalidationBlocksRequestBeforeNetwork() = runTest {
        var valid = true
        var calls = 0
        val engine = AiSwipeEngine(this, AiSwipeProvider { calls++; prediction }, { testScheduler.currentTime })
        engine.submit(request, { valid }, {}, { fail() })
        valid = false
        advanceUntilIdle()
        assertEquals(0, calls)
    }
    @Test fun lateNonCooperativeProviderNeverPublishesAndCallsDoNotOverlap() = runTest {
        val results = mutableListOf<AiSwipeResult>()
        var concurrent = 0
        var maximum = 0
        val engine = AiSwipeEngine(this, AiSwipeProvider {
            concurrent++; maximum = maxOf(maximum, concurrent)
            try { withContext(NonCancellable) { delay(600) }; prediction } finally { concurrent-- }
        }, { testScheduler.currentTime })
        engine.submit(request, { true }, {}, results::add)
        advanceTimeBy(300)
        engine.submit(request, { true }, {}, results::add)
        advanceUntilIdle()
        assertEquals(1, maximum)
        assertEquals(listOf(prediction), results)
    }
    @Test fun failedRequestsBackOffWithoutRetries() = runTest {
        val statuses = mutableListOf<AiSwipeStatus>()
        var calls = 0
        val engine = AiSwipeEngine(this, AiSwipeProvider { calls++; error("network error") }, { testScheduler.currentTime })
        engine.submit(request, { true }, statuses::add, { fail() })
        advanceUntilIdle()
        engine.submit(request, { true }, statuses::add, { fail() })
        advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue(statuses.contains(AiSwipeStatus.UNAVAILABLE))
        assertEquals(AiSwipeStatus.RATE_LIMITED, statuses.last())
    }
    @Test fun timeoutFallsBackToLocalWithoutPublishing() = runTest {
        val statuses = mutableListOf<AiSwipeStatus>()
        val engine = AiSwipeEngine(this, AiSwipeProvider { delay(10_000); prediction }, { testScheduler.currentTime })
        engine.submit(request, { true }, statuses::add, { fail() })
        advanceUntilIdle()
        assertEquals(AiSwipeStatus.UNAVAILABLE, statuses.last())
        assertTrue(testScheduler.currentTime < 4000)
    }
    @Test fun clientRequestBudgetCapsOneMinute() = runTest {
        var calls = 0
        val starts = mutableListOf<Long>()
        val engine = AiSwipeEngine(this, AiSwipeProvider {
            calls++; starts.add(testScheduler.currentTime); prediction
        }, { testScheduler.currentTime })
        repeat(35) {
            engine.submit(request, { true }, {}, {})
            advanceUntilIdle()
        }
        assertEquals(30, calls)
        assertTrue(starts.zipWithNext().all { (a, b) -> b - a >= 1000 })
    }
    @Test fun sentenceRequestHasLongerTimeoutButRetainsStaleResultProtection() = runTest {
        val trace = AiSwipeTrace(listOf(AiSwipeTraceKey("a", 1f, 1f, 1f, 1f),
            AiSwipeTraceKey("b", 2f, 1f, 1f, 1f)), listOf(
            AiSwipeTracePoint(1f, 1f, 0), AiSwipeTracePoint(2f, 1f, 100)))
        val request = AiSwipeRequest("", emptyList(), "en", false, trace)
        val prediction = AiSwipeResult("", "", listOf("A b"))
        var accepted = 0
        val engine = AiSwipeEngine(this, AiSwipeProvider { delay(5_000); prediction }, { testScheduler.currentTime })
        engine.submit(request, { true }, {}, { accepted++ })
        advanceUntilIdle()
        assertEquals(1, accepted)
        engine.submit(request, { true }, {}, { accepted++ })
        advanceTimeBy(1_000)
        engine.invalidate()
        advanceUntilIdle()
        assertEquals(1, accepted)
    }

}
