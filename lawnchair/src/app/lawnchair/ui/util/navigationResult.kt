package app.lawnchair.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import app.lawnchair.ui.preferences.LocalNavController

@Composable
fun <T> resultSender(): (T) -> Unit {
    val navController = LocalNavController.current
    return { item: T ->
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set("result", item)
        navController.popBackStack()
        Unit
    }
}

@Composable
fun <T> OnResult(callback: (result: T) -> Unit) {
    val currentCallback = rememberUpdatedState(callback)
    var fired by remember { mutableStateOf(false) }

    val handle = LocalNavController.current.currentBackStackEntry?.savedStateHandle
    val result = handle?.getLiveData<T>("result")?.observeAsState()

    SideEffect {
        result?.value?.let {
            if (fired) return@let
            fired = true
            currentCallback.value(it)
            handle.remove<T>("result")
            Unit
        }
    }
}
