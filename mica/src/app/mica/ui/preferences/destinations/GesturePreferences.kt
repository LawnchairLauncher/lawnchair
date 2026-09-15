package app.mica.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.mica.gestures.handlers.SleepMode
import app.mica.preferences.getAdapter
import app.mica.preferences2.preferenceManager2
import app.mica.ui.preferences.LocalIsExpandedScreen
import app.mica.ui.preferences.components.GestureHandlerPreference
import app.mica.ui.preferences.components.controls.ListPreference
import app.mica.ui.preferences.components.layout.PreferenceGroup
import app.mica.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

@Composable
fun GesturePreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager2()
    PreferenceLayout(
        label = stringResource(id = R.string.gestures_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup {
            GestureHandlerPreference(
                adapter = prefs.doubleTapGestureHandler.getAdapter(),
                label = stringResource(id = R.string.gesture_double_tap),
            )
            GestureHandlerPreference(
                adapter = prefs.swipeUpGestureHandler.getAdapter(),
                label = stringResource(id = R.string.gesture_swipe_up),
            )
            GestureHandlerPreference(
                adapter = prefs.swipeDownGestureHandler.getAdapter(),
                label = stringResource(id = R.string.gesture_swipe_down),
            )
            GestureHandlerPreference(
                adapter = prefs.twoFingerSwipeUpGestureHandler.getAdapter(),
                label = stringResource(id = R.string.gesture_two_finger_swipe_up),
            )
            GestureHandlerPreference(
                adapter = prefs.twoFingerSwipeDownGestureHandler.getAdapter(),
                label = stringResource(id = R.string.gesture_two_finger_swipe_down),
            )
            GestureHandlerPreference(
                adapter = prefs.homePressGestureHandler.getAdapter(),
                label = stringResource(id = R.string.gesture_home_tap),
            )
            GestureHandlerPreference(
                adapter = prefs.backPressGestureHandler.getAdapter(),
                label = stringResource(id = R.string.gesture_back_tap),
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.sleep_mode_label)) {
            ListPreference(
                adapter = prefs.sleepMode.getAdapter(),
                entries = SleepMode.entries(),
                label = stringResource(id = R.string.sleep_mode_label),
            )
        }
    }
}
