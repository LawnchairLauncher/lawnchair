package app.lawnchair.search.algorithms

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart

/** Slow/failing providers cannot hold back other local results; cancellation remains cancellation. */
internal fun <T> progressiveResults(providers: List<Flow<List<T>>>): Flow<List<T>> {
    if (providers.isEmpty()) return flowOf(emptyList())
    return combine(
        providers.map { provider ->
            provider.onStart { emit(emptyList()) }.catch { error ->
                if (error is CancellationException) throw error
                emit(emptyList())
            }
        },
    ) { results -> results.flatMap { it } }
}
