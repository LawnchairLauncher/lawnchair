package app.lawnchair.preferences2

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.util.subscribeBlocking
import com.patrykmichalik.opto.core.PreferenceImpl
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.domain.Preference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun <C, S> Preference<C, S, *>.asState() = get().collectAsStateWithLifecycle(initialValue = firstBlocking())

@Composable
fun <C, S> PreferenceImpl<C, S>.asState() = get().collectAsStateWithLifecycle(initialValue = firstCached(preferenceManager2()))

fun <C, S> PreferenceImpl<C, S>.subscribeBlocking(
    prefs2: PreferenceManager2,
    scope: CoroutineScope,
    block: (C) -> Unit,
) {
    val initial = firstCached(prefs2)
    block(initial)
    get()
        .distinctUntilChanged()
        .dropWhile { it == initial }
        .onEach { block(it) }
        .launchIn(scope = scope)
}

fun <C, S> Preference<C, S, *>.subscribeBlocking(
    scope: CoroutineScope,
    block: (C) -> Unit,
) {
    get().subscribeBlocking(scope, block)
}
