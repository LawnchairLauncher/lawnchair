package app.lawnchair.util

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

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
