package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.GestureHandlerPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
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
            Item {
                GestureHandlerPreference(
                    adapter = prefs.doubleTapGestureHandler.getAdapter(),
                    label = stringResource(id = R.string.gesture_double_tap),
                )
            }
            Item {
                GestureHandlerPreference(
                    adapter = prefs.swipeUpGestureHandler.getAdapter(),
                    label = stringResource(id = R.string.gesture_swipe_up),
                )
            }
            Item {
                GestureHandlerPreference(
                    adapter = prefs.swipeDownGestureHandler.getAdapter(),
                    label = stringResource(id = R.string.gesture_swipe_down),
                )
            }
            Item {
                GestureHandlerPreference(
                    adapter = prefs.homePressGestureHandler.getAdapter(),
                    label = stringResource(id = R.string.gesture_home_tap),
                )
            }
            Item {
                GestureHandlerPreference(
                    adapter = prefs.backPressGestureHandler.getAdapter(),
                    label = stringResource(id = R.string.gesture_back_tap),
                )
            }
        }
    }
}
