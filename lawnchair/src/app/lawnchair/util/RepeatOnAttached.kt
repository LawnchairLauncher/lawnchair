package app.lawnchair.util

import android.view.View
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun View.repeatOnAttached(block: suspend CoroutineScope.() -> Unit) {
    var launchedJob: Job? = null

    val mutext = Mutex()
    observeAttachedState { isAttached ->
        if (isAttached) {
            launchedJob = MainScope().launch(
                context = Dispatchers.Main.immediate,
                start = CoroutineStart.UNDISPATCHED
            ) {
                mutext.withLock {
                    coroutineScope {
                        block()
                    }
                }
            }
            return@observeAttachedState
        }
        launchedJob?.cancel()
        launchedJob = null
    }
}
