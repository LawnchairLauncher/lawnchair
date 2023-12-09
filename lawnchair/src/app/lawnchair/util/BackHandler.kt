package app.lawnchair.util

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle

@Composable
fun BackHandler(
    onBack: () -> Unit,
) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val currentOnBack by rememberUpdatedState(onBack)
    val resumed = lifecycleState().isAtLeast(Lifecycle.State.RESUMED)

    DisposableEffect(resumed, backDispatcher) {
        if (!resumed || backDispatcher == null) {
            return@DisposableEffect onDispose { }
        }
        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                currentOnBack()
            }
        }
        backDispatcher.addCallback(backCallback)
        onDispose { backCallback.remove() }
    }
}
