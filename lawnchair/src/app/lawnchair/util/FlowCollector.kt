package app.lawnchair.util

import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class FlowCollector<T>(
    private val flow: Flow<T>,
    private val callback: Listener<T>,
) {

    private val scope = MainScope()
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            flow.collect(callback::onItem)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    interface Listener<T> {
        fun onItem(item: T)
    }
}
