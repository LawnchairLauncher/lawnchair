package app.lawnchair.preferences2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import app.lawnchair.util.subscribeBlocking
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.domain.Preference
import kotlinx.coroutines.CoroutineScope

@Composable
fun <C, S> Preference<C, S, *>.asState() = get().collectAsState(initial = firstBlocking())

fun <C, S> Preference<C, S, *>.subscribeBlocking(
    scope: CoroutineScope,
    block: (C) -> Unit,
) {
    get().subscribeBlocking(scope, block)
}
