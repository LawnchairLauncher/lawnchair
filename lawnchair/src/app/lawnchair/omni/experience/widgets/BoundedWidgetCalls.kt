package app.lawnchair.omni.experience.widgets

import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/**
 * Binder calls are not reliably interruptible. Waiting with withContext(IO) alone can therefore
 * keep its caller suspended after cancellation. Detach the waiter on timeout, bound outstanding
 * work, and never retain an Activity in [operation]. A late result cannot update a closed screen.
 */
internal class BoundedWidgetCalls(workerCount: Int = 2, queueCapacity: Int = 8) : Closeable {
    private val executor = ThreadPoolExecutor(
        workerCount,
        workerCount,
        30L,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(queueCapacity),
        { task -> Thread(task, "OmniWidgetIO").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    ).apply { allowCoreThreadTimeOut(true) }

    suspend fun <T> call(timeoutMillis: Long = 2_500, discard: (T) -> Unit = {}, operation: () -> T): T = withTimeout(timeoutMillis) {
        suspendCancellableCoroutine { continuation ->
            val recipient = AtomicReference<CancellableContinuation<T>?>(continuation)
            val task = FutureTask(
                Callable<Unit> {
                    val result = runCatching(operation)
                    val waiter = recipient.getAndSet(null)
                    if (waiter != null) {
                        result.fold(
                            { value ->
                                waiter.resume(value) { _, abandoned, _ ->
                                    // Prompt cancellation may run this hook on Main. Cleanup stays off it.
                                    try {
                                        executor.execute { discard(abandoned) }
                                    } catch (error: java.util.concurrent.RejectedExecutionException) {
                                        java.util.logging.Logger.getLogger("OmniWidgetIO").log(java.util.logging.Level.WARNING, "Abandoned widget cleanup unavailable", error)
                                    }
                                }
                            },
                            waiter::resumeWithException,
                        )
                    } else if (result.isSuccess) {
                        discard(result.getOrThrow())
                    }
                },
            )
            continuation.invokeOnCancellation {
                recipient.set(null)
                task.cancel(true)
                executor.remove(task)
            }
            try {
                executor.execute(task)
            } catch (error: java.util.concurrent.RejectedExecutionException) {
                recipient.set(null)
                continuation.resumeWithException(error)
            }
        }
    }

    override fun close() {
        executor.shutdownNow()
    }

    companion object {
        // Process-scoped and bounded even if a broken system service outlives an Activity.
        val shared by lazy { BoundedWidgetCalls() }
    }
}
