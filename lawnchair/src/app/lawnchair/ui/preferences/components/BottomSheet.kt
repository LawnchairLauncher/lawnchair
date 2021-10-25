package app.lawnchair.ui.preferences.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.util.portal.Portal
import app.lawnchair.util.BackHandler
import com.google.accompanist.insets.LocalWindowInsets
import kotlinx.coroutines.launch
import java.lang.Integer.max

@ExperimentalMaterialApi
@Composable
fun BottomSheet(
    modifier: Modifier = Modifier,
    sheetState: BottomSheetState = rememberBottomSheetState(initialValue = ModalBottomSheetValue.Hidden),
    scrimColor: Color = BottomSheetDefaults.scrimColor,
    sheetShape: CornerBasedShape = MaterialTheme.shapes.large,
    sheetBackgroundColor: Color = MaterialTheme.colors.surface,
    sheetContent: @Composable () -> Unit,
) {
    val currentSheetContent by rememberUpdatedState(sheetContent)
    val modalBottomSheetState = sheetState.modalBottomSheetState
    val scope = rememberCoroutineScope()

    if (modalBottomSheetState != null) {
        Portal {
            ModalBottomSheetLayout(
                modifier = modifier,
                sheetState = modalBottomSheetState,
                sheetContent = { StatusBarOffset(currentSheetContent) },
                scrimColor = scrimColor,
                sheetShape = sheetShape.copy(
                    bottomStart = CornerSize(0.dp),
                    bottomEnd = CornerSize(0.dp)
                ),
                sheetBackgroundColor = sheetBackgroundColor
            ) {
                BackHandler {
                    scope.launch { sheetState.onBackPressed() }
                }
            }
        }
        if (!sheetState.isChangingState && !modalBottomSheetState.isVisible) {
            sheetState.modalBottomSheetState = null
        }
    }
}

@Composable
fun StatusBarOffset(content: @Composable () -> Unit) {
    val windowInsets = LocalWindowInsets.current
    val statusBarHeight = max(windowInsets.statusBars.top, windowInsets.displayCutout.top)
    val topOffset = statusBarHeight + with(LocalDensity.current) { 8.dp.roundToPx() }

    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val newConstraints = Constraints(
                    minWidth = constraints.minWidth,
                    maxWidth = constraints.maxWidth,
                    minHeight = constraints.minHeight,
                    maxHeight = constraints.maxHeight - topOffset
                )
                val placeable = measurable.measure(newConstraints)

                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0)
                }
            }
    ) {
        content()
    }
}

@ExperimentalMaterialApi
@Composable
fun rememberBottomSheetState(
    initialValue: ModalBottomSheetValue,
    animationSpec: AnimationSpec<Float> = SwipeableDefaults.AnimationSpec,
    confirmStateChange: (ModalBottomSheetValue) -> Boolean = { true }
): BottomSheetState =
    rememberSaveable(
        saver = BottomSheetState.Saver(
            animationSpec = animationSpec,
            confirmStateChange = confirmStateChange
        )
    ) {
        BottomSheetState(initialValue, animationSpec, confirmStateChange)
    }

@ExperimentalMaterialApi
class BottomSheetState(
    private val initialValue: ModalBottomSheetValue,
    private val animationSpec: AnimationSpec<Float> = SwipeableDefaults.AnimationSpec,
    private val confirmStateChange: (ModalBottomSheetValue) -> Boolean = { true }
) {
    internal var isChangingState by mutableStateOf(false)
    internal var modalBottomSheetState by mutableStateOf<ModalBottomSheetState?>(null)

    suspend fun show() {
        transitionState { it.show() }
    }

    suspend fun hide() {
        transitionState { it.hide() }
    }

    suspend fun snapTo(targetValue: ModalBottomSheetValue) {
        transitionState {
            it.snapTo(targetValue)
        }
    }

    suspend fun animateTo(targetValue: ModalBottomSheetValue, anim: AnimationSpec<Float> = animationSpec) {
        transitionState {
            it.animateTo(targetValue, anim)
        }
    }

    private inline fun transitionState(block: (ModalBottomSheetState) -> Unit) {
        try {
            isChangingState = true
            block(getModalBottomSheetState())
        } finally {
            isChangingState = false
        }
    }

    private fun getModalBottomSheetState(): ModalBottomSheetState {
        if (modalBottomSheetState == null) {
            modalBottomSheetState =
                ModalBottomSheetState(initialValue, animationSpec, confirmStateChange)
        }
        return modalBottomSheetState!!
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

object BottomSheetDefaults {
    val scrimColor: Color
        @Composable
        get() = if (MaterialTheme.colors.isLight) {
            MaterialTheme.colors.onBackground.copy(alpha = 0.32F)
        } else {
            MaterialTheme.colors.background.copy(alpha = 0.32F)
        }
}