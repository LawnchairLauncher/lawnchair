package app.lawnchair.util

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

private val LocalLifecycleState = compositionLocalOf<Lifecycle.State> {
    error("CompositionLocal LocalLifecycleState not present")
}

@Composable
fun lifecycleState(): Lifecycle.State = LocalLifecycleState.current

@Composable
fun ProvideLifecycleState(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLifecycleState provides observeLifecycleState()) {
        content()
    }
}

@Composable
private fun observeLifecycleState(): Lifecycle.State {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var state by remember(lifecycle) { mutableStateOf(lifecycle.currentState) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            state = event.targetState
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    return state
}

fun Context.lookupLifecycleOwner(): LifecycleOwner? {
    return when (this) {
        is LifecycleOwner -> this
        is ContextWrapper -> baseContext.lookupLifecycleOwner()
        else -> null
    }
}
