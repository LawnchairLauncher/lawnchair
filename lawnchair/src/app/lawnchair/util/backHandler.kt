package app.lawnchair.util

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState

@Composable
fun backHandler(onBack: () -> Unit) {
    val onBackPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
    val onBackPressedDispatcher = onBackPressedDispatcherOwner?.onBackPressedDispatcher
    val currentOnBack = rememberUpdatedState(onBack)

    DisposableEffect(onBackPressedDispatcher) {
        if (onBackPressedDispatcher == null) {
            return@DisposableEffect onDispose { }
        }
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                currentOnBack.value()
            }
        }
        onBackPressedDispatcher.addCallback(onBackPressedCallback)
        onDispose { onBackPressedCallback.remove() }
    }
}
