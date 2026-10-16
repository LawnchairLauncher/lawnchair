package app.lawnchair.omni.experience

import app.lawnchair.omni.experience.widgets.BoundedWidgetCalls
import app.lawnchair.ui.preferences.components.layout.dividerCount
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLoadingTest {
    private fun awaitIgnoringInterrupts(latch: CountDownLatch) {
        while (true) {
            try {
                if (latch.await(2, TimeUnit.SECONDS)) return
            } catch (_: InterruptedException) { /* Simulate a blocked Binder. */ }
        }
    }

    @Test(timeout = 5_000)
    fun timeoutDoesNotWaitForUninterruptibleProviderAndUiLoopKeepsRunning() = runBlocking {
        BoundedWidgetCalls(workerCount = 1).use { calls ->
            val release = CountDownLatch(1)
            val ticks = AtomicInteger()
            val ui = launch {
                while (true) {
                    ticks.incrementAndGet()
                    delay(5)
                }
            }
            var timedOut = false
            try {
                val elapsed = measureTimeMillis {
                    try {
                        calls.call(timeoutMillis = 100) { awaitIgnoringInterrupts(release) }
                    } catch (_: TimeoutCancellationException) {
                        timedOut = true
                    }
                }
                assertTrue(timedOut)
                assertTrue("Timeout waited $elapsed ms for provider", elapsed < 1_000)
                assertTrue("UI event loop was blocked", ticks.get() >= 2)
            } finally {
                release.countDown()
                ui.cancelAndJoin()
            }
        }
    }

    @Test(timeout = 5_000)
    fun cancellationDropsLateResultAndCleansAllocatedResourceOffCallerThread() = runBlocking {
        BoundedWidgetCalls(workerCount = 1).use { calls ->
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val discarded = CountDownLatch(1)
            val cleanupThread = AtomicReference<String>()
            var delivered = false
            val request = launch {
                val id = calls.call(discard = { value: Int ->
                    assertEquals(42, value)
                    cleanupThread.set(Thread.currentThread().name)
                    discarded.countDown()
                }) {
                    started.countDown()
                    awaitIgnoringInterrupts(release)
                    42
                }
                delivered = id == 42
            }
            try {
                while (started.count > 0) delay(5)
                request.cancelAndJoin()
                release.countDown()
                while (discarded.count > 0) delay(5)
                assertFalse(delivered)
                assertTrue(cleanupThread.get().startsWith("OmniWidgetIO"))
            } finally {
                release.countDown()
                request.cancelAndJoin()
            }
        }
    }

    @Test(timeout = 5_000)
    fun queueIsBoundedAndSaturatedWorkerFailsWithoutBlockingUi() = runBlocking {
        BoundedWidgetCalls(workerCount = 1, queueCapacity = 1).use { calls ->
            val release = CountDownLatch(1)
            val started = CountDownLatch(1)
            val first = launch {
                calls.call {
                    started.countDown()
                    awaitIgnoringInterrupts(release)
                }
            }
            while (started.count > 0) delay(5)
            val queued = launch { calls.call { 2 } }
            delay(20)
            var rejected = false
            try {
                try {
                    calls.call { 3 }
                } catch (_: RejectedExecutionException) {
                    rejected = true
                }
                assertTrue(rejected)
            } finally {
                release.countDown()
                first.cancelAndJoin()
                queued.cancelAndJoin()
            }
        }
    }

    @Test(timeout = 5_000)
    fun cancelledQueuedWorkNeverCallsProvider() = runBlocking {
        BoundedWidgetCalls(workerCount = 1, queueCapacity = 1).use { calls ->
            val release = CountDownLatch(1)
            val started = CountDownLatch(1)
            val first = launch {
                calls.call {
                    started.countDown()
                    awaitIgnoringInterrupts(release)
                }
            }
            while (started.count > 0) delay(5)
            val invocations = AtomicInteger()
            val queued = launch { calls.call { invocations.incrementAndGet() } }
            delay(20)
            queued.cancelAndJoin()
            release.countDown()
            first.join()
            assertEquals(7, calls.call { 7 })
            assertEquals(0, invocations.get())
        }
    }

    @Test(timeout = 5_000)
    fun providerFailureIsPropagatedAndNextLoadCanSucceed() = runBlocking {
        BoundedWidgetCalls().use { calls ->
            var failed = false
            try {
                calls.call<Int> { throw IllegalStateException("removed provider") }
            } catch (_: IllegalStateException) {
                failed = true
            }
            assertTrue(failed)
            assertEquals(8, calls.call { 8 })
        }
    }

    @Test(timeout = 5_000)
    fun emptyGroupAndSkippedDividersNeverCreateNegativeHeight() {
        assertEquals(0, dividerCount(0, 0))
        assertEquals(0, dividerCount(1, 0))
        assertEquals(2, dividerCount(3, 0))
        assertEquals(1, dividerCount(3, 1))
        assertEquals(0, dividerCount(3, 20))
        assertEquals(2, dividerCount(3, -1))
    }
}
