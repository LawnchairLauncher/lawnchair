package app.lawnchair.search.algorithms

import android.util.Log
import com.android.launcher3.LauncherModel
import com.android.launcher3.model.AllAppsList
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "ModelLoadedExtensions"

/**
 * Maximum number of times [runWhenModelLoaded] retries enqueuing before giving up
 * (~2s total with [MODEL_LOAD_RETRY_DELAY_MS]).
 */
private const val MODEL_LOAD_MAX_RETRIES = 20

/** Delay between enqueue attempts in [runWhenModelLoaded]. */
private const val MODEL_LOAD_RETRY_DELAY_MS = 100L

/**
 * Runs [block] with the loaded all-apps data, and returns a [Job] that owns both the wait for
 * the model and the delivery of [block]. Cancelling that job cancels a pending retry *and*
 * prevents [block] from running if it hasn't started yet, so a search cancelled while the
 * drawer closes ([com.android.launcher3.allapps.search.AllAppsSearchBarController.reset]) can
 * never deliver stale results.
 *
 * [LauncherModel.enqueueModelUpdateTask] silently drops its task whenever the model is not
 * loaded at execution time — including the race where the model unloads between the caller's
 * check and the task running — which would leave a search with no results and no callback (see
 * issue #7325). To close that gap, this enqueues a task that confirms it actually executed; if
 * it was dropped, it waits and retries, up to [MODEL_LOAD_MAX_RETRIES] times. On exhaustion it
 * logs rather than failing silently. The block runs on the model executor thread, as with a
 * normal [LauncherModel.enqueueModelUpdateTask].
 */
fun LauncherModel.runWhenModelLoaded(
    scope: CoroutineScope,
    block: (apps: AllAppsList) -> Unit,
): Job = scope.launch {
    // Captured so the model task (which runs with LauncherModel as its receiver, shadowing
    // CoroutineScope.isActive) can still observe cancellation of this coroutine.
    val job = checkNotNull(coroutineContext[Job])
    // A single deferred shared across attempts. MODEL_EXECUTOR runs tasks in order, so if the
    // executor is busy several retry tasks may already be queued when the model finally loads;
    // gating on this shared latch ensures block() runs exactly once no matter how many queued
    // tasks pass the isActive check.
    val executed = CompletableDeferred<Boolean>()
    repeat(MODEL_LOAD_MAX_RETRIES) {
        enqueueModelUpdateTask { _, _, apps ->
            // enqueueModelUpdateTask only runs this when the model is loaded, so reaching here
            // confirms execution. Re-check cancellation so a search cancelled during the wait
            // does not deliver stale results, and the completion latch so requeued tasks don't
            // deliver duplicates.
            if (job.isActive && !executed.isCompleted) {
                block(apps)
                executed.complete(true)
            }
        }
        // If the task was dropped (model not loaded at execution time) it never completes the
        // deferred, so this bounded await times out and we retry. Once any attempt has run,
        // the deferred is already complete and this returns immediately. withTimeoutOrNull
        // returns null on timeout without cancelling this coroutine.
        val ran = withTimeoutOrNull(MODEL_LOAD_RETRY_DELAY_MS) { executed.await() }
        coroutineContext.ensureActive()
        if (ran == true) {
            return@launch
        }
    }
    Log.w(TAG, "Model did not load in time; search results were not produced.")
}
