package app.lawnchair.preferences2

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.util.subscribeBlocking
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.domain.Preference
import kotlinx.coroutines.CoroutineScope

@Composable
fun <C, S> Preference<C, S, *>.asState() = asState(initialValue = firstBlocking())

/** Explicit asynchronous initialization for UI that must draw before storage becomes available. */
@Composable
fun <C, S> Preference<C, S, *>.asState(initialValue: C) = get().collectAsStateWithLifecycle(initialValue = initialValue)

fun <C, S> Preference<C, S, *>.subscribeBlocking(
    scope: CoroutineScope,
    block: (C) -> Unit,
) {
    get().subscribeBlocking(scope, block)
}
