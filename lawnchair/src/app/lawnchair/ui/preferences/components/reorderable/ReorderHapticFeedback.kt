package app.lawnchair.ui.preferences.components.reorderable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.android.launcher3.Utilities

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
    val view = LocalView.current

    val reorderHapticFeedback = remember {
        object : ReorderHapticFeedback {
            override fun performHapticFeedback(type: ReorderHapticFeedbackType) {
                if (Utilities.ATLEAST_U) {
                    when (type) {
                        ReorderHapticFeedbackType.START ->
                            ViewCompat.performHapticFeedback(
                                view,
                                HapticFeedbackConstantsCompat.GESTURE_START,
                            )

                        ReorderHapticFeedbackType.MOVE ->
                            ViewCompat.performHapticFeedback(
                                view,
                                HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK,
                            )

                        ReorderHapticFeedbackType.END ->
                            ViewCompat.performHapticFeedback(
                                view,
                                HapticFeedbackConstantsCompat.GESTURE_END,
                            )
                    }
                }
            }
        }
    }

    return reorderHapticFeedback
}
