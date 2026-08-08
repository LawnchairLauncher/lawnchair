package app.lawnchair.ui.preferences.components.reorderable

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.android.launcher3.Utilities
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken

enum class ReorderHapticFeedbackType {
    START,
    MOVE,
    END,
    CANCEL,
}

interface ReorderHapticFeedback {
    fun performHapticFeedback(type: ReorderHapticFeedbackType) {}
}

@Composable
fun rememberReorderHapticFeedback(): ReorderHapticFeedback {
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(LocalContext.current)

    val reorderHapticFeedback = remember {
        object : ReorderHapticFeedback {
            override fun performHapticFeedback(type: ReorderHapticFeedbackType) {
                if (Utilities.ATLEAST_U) {
                    mMSDLPlayerWrapper.playToken(
                        when (type) {
                            ReorderHapticFeedbackType.START -> MSDLToken.START
                            ReorderHapticFeedbackType.MOVE -> MSDLToken.DRAG_INDICATOR_DISCRETE
                            ReorderHapticFeedbackType.END -> MSDLToken.STOP
                            ReorderHapticFeedbackType.CANCEL -> MSDLToken.CANCEL
                        },
                    )
                }
            }
        }
    }

    return reorderHapticFeedback
}

@Composable
internal fun ObserveReorderHapticFeedback(interactionSource: MutableInteractionSource) {
    val haptic = rememberReorderHapticFeedback()
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start ->
                    haptic.performHapticFeedback(ReorderHapticFeedbackType.START)

                is DragInteraction.Stop ->
                    haptic.performHapticFeedback(ReorderHapticFeedbackType.END)

                is DragInteraction.Cancel ->
                    haptic.performHapticFeedback(ReorderHapticFeedbackType.CANCEL)
            }
        }
    }
}
