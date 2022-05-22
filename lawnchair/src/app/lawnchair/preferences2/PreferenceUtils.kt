package app.lawnchair.preferences2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.patrykmichalik.preferencemanager.Preference
import com.patrykmichalik.preferencemanager.firstBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun <C, S> Preference<C, S>.asState() = get().collectAsState(initial = firstBlocking())

fun <C, S> Preference<C, S>.subscribeBlocking(
    scope: CoroutineScope,
    block: (C) -> Unit,
) {
    block(firstBlocking())
    get()
        .onEach { block(it) }
        .drop(1)
        .distinctUntilChanged()
        .launchIn(scope = scope)
}
