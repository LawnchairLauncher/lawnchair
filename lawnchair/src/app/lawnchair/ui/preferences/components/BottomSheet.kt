package app.lawnchair.ui.preferences.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import app.lawnchair.ui.util.portal.Portal
import app.lawnchair.util.backHandler
import kotlinx.coroutines.launch

@ExperimentalMaterialApi
@Composable
fun BottomSheet(
    sheetContent: @Composable ColumnScope.() -> Unit,
    sheetState: BottomSheetState = rememberBottomSheetState(initialValue = ModalBottomSheetValue.Hidden),
) {
    val currentSheetContent by rememberUpdatedState(sheetContent)
    val modalBottomSheetState = sheetState.modalBottomSheetState
    val scope = rememberCoroutineScope()

    if (modalBottomSheetState != null) {
        Portal {
            ModalBottomSheetLayout(
                sheetState = modalBottomSheetState,
                sheetContent = currentSheetContent
            ) {
                backHandler {
                    scope.launch { sheetState.onBackPressed() }
                }
            }
        }
        if (!sheetState.isAnimatingShow && !modalBottomSheetState.isVisible) {
            sheetState.modalBottomSheetState = null
        }
    }
}

@ExperimentalMaterialApi
@Composable
fun rememberBottomSheetState(
    initialValue: ModalBottomSheetValue,
    animationSpec: AnimationSpec<Float> = SwipeableDefaults.AnimationSpec,
    confirmStateChange: (ModalBottomSheetValue) -> Boolean = { true }
): BottomSheetState {
    return rememberSaveable(
        saver = BottomSheetState.Saver(
            animationSpec = animationSpec,
            confirmStateChange = confirmStateChange
        )
    ) {
        BottomSheetState(initialValue, animationSpec, confirmStateChange)
    }
}

@ExperimentalMaterialApi
class BottomSheetState(
    private val initialValue: ModalBottomSheetValue,
    private val animationSpec: AnimationSpec<Float> = SwipeableDefaults.AnimationSpec,
    private val confirmStateChange: (ModalBottomSheetValue) -> Boolean = { true }
) {
    internal var isAnimatingShow by mutableStateOf(false)
    internal var modalBottomSheetState by mutableStateOf<ModalBottomSheetState?>(null)

    suspend fun show() {
        try {
            isAnimatingShow = true
            if (modalBottomSheetState == null) {
                modalBottomSheetState = ModalBottomSheetState(initialValue, animationSpec, confirmStateChange)
            }
            modalBottomSheetState!!.show()
        } finally {
            isAnimatingShow = false
        }
    }

    suspend fun hide() {
        modalBottomSheetState?.hide()
    }

    suspend fun onBackPressed() {
        if (confirmStateChange(ModalBottomSheetValue.Hidden)) {
            hide()
        }
    }

    companion object {
        /**
         * The default [Saver] implementation for [BottomSheetState].
         */
        fun Saver(
            animationSpec: AnimationSpec<Float>,
            confirmStateChange: (ModalBottomSheetValue) -> Boolean
        ): Saver<BottomSheetState, *> = Saver(
            save = { it.modalBottomSheetState?.currentValue },
            restore = {
                BottomSheetState(
                    initialValue = it,
                    animationSpec = animationSpec,
                    confirmStateChange = confirmStateChange
                )
            }
        )
    }
}
