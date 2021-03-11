package ch.deletescape.lawnchair.compose.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable

@Composable
fun DockPreferences(interactor: PreferenceInteractor) {
    Column {
        PreferenceGroup {
            SliderPreference(
                label = "Dock Icons",
                value = interactor.hotseatColumns.value,
                onValueChange = { interactor.setHotseatColumns(it) },
                steps = 3,
                valueRange = 3.0F..7.0F
            )
        }
    }
}