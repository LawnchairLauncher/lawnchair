package app.lawnchair.ui.preferences.components.reorderable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.android.launcher3.Utilities
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken

enum class ReorderHapticFeedbackType {
    START,
    MOVE,
    END,
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
                        },
                    )
                }
            }
        }
    }

    return reorderHapticFeedback
}
