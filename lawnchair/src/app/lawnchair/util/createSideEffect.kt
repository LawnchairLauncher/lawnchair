package app.lawnchair.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

class PropsContainer<P>(var props: P)

class SideEffect<S, P>(
    val consume: @Composable (@Composable (S) -> Unit) -> Unit,
    val provide: @Composable (P) -> Unit
)

inline fun <S, P> createSideEffect(crossinline reducePropsToState: (List<P>) -> S): SideEffect<S, P> {
    val mountedInstances = mutableListOf<PropsContainer<P>>()
    var currentState = reducePropsToState(emptyList())
    var onChangeHandler: (() -> Unit)? = null

    val emitChange = {
        currentState = reducePropsToState(mountedInstances.map { it.props })
        onChangeHandler?.invoke()
    }

    return SideEffect(
        consume = @Composable { block ->
            val localState = remember { mutableStateOf(currentState) }
            DisposableEffect(null) {
                onChangeHandler = { localState.value = currentState }
                onDispose { onChangeHandler = null }
            }
            block(localState.value)
        },
        provide = @Composable { props ->
            val currentProps = remember { PropsContainer(props) }
            DisposableEffect(key1 = currentProps) {
                mountedInstances.add(currentProps)
                onDispose {
                    mountedInstances.remove(currentProps)
                    emitChange()
                }
            }
            DisposableEffect(key1 = props) {
                currentProps.props = props
                emitChange()
                onDispose { }
            }
        }
    )
}
