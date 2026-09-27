package app.lawnchair.search.algorithms

import com.android.launcher3.LauncherModel
import com.android.launcher3.model.AllAppsList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Maximum number of times [runWhenModelLoaded] polls for the model to finish loading before
 * giving up (~2s total with [MODEL_LOAD_RETRY_DELAY_MS]).
 */
private const val MODEL_LOAD_MAX_RETRIES = 20

/** Delay between model-loaded polls in [runWhenModelLoaded]. */
private const val MODEL_LOAD_RETRY_DELAY_MS = 100L

/**
 * Runs [block] with the loaded all-apps data.
 *
 * [LauncherModel.enqueueModelUpdateTask] silently drops the task if the model is not yet
 * loaded (e.g. a search issued during a model reload), which would produce no results and no
 * callback at all (see issue #7325). To avoid that, this waits until the model reports loaded
 * before enqueuing, retrying a bounded number of times so it never blocks indefinitely.
 *
 * If the model is already loaded the task is enqueued immediately and this returns `null`.
 * Otherwise it launches a polling coroutine on [scope] and returns its [Job] so the caller can
 * cancel a pending retry (e.g. when the search is cancelled or the drawer closes).
 */
fun LauncherModel.runWhenModelLoaded(
    scope: CoroutineScope,
    block: (apps: AllAppsList) -> Unit,
): Job? {
    val enqueue = { enqueueModelUpdateTask { _, _, apps -> block(apps) } }
    if (isModelLoaded()) {
        enqueue()
        return null
    }
    return scope.launch {
        repeat(MODEL_LOAD_MAX_RETRIES) {
            delay(MODEL_LOAD_RETRY_DELAY_MS)
            if (isModelLoaded()) {
                enqueue()
                return@launch
            }
        }
    }
}
