package app.lawnchair.ui.preferences.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupScope
import com.android.launcher3.Flags
import com.android.launcher3.R

@Composable
fun PreferenceGroupScope.AppDrawerHapticFeedbackPreference() {
    val context = LocalContext.current
    val isSupported = remember { isDrawerHapticFeedbackSupported(context) }

    if (isSupported) {
        Item {
            SwitchPreference(
                adapter = preferenceManager2().appDrawerHapticFeedback.getAdapter(),
                label = stringResource(id = R.string.app_drawer_haptic_feedback_label),
            )
        }
    }
}

private fun isDrawerHapticFeedbackSupported(context: Context): Boolean {
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return false
    if (!vibrator.hasVibrator()) return false

    if (Flags.msdlFeedback() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return vibrator.arePrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)[0] ||
            vibrator.areEffectsSupported(VibrationEffect.EFFECT_CLICK)[0] == Vibrator.VIBRATION_EFFECT_SUPPORT_YES
    }

    return true
}
