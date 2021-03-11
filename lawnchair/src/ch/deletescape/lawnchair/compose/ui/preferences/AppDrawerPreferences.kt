package ch.deletescape.lawnchair.compose.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable

@Composable
fun AppDrawerPreferences(interactor: PreferenceInteractor) {
    Column {
        PreferenceGroup {
            SliderPreference(
                label = "App Drawer Columns",
                value = interactor.allAppsColumns.value,
                onValueChange = { interactor.setAllAppsColumns(it) },
                steps = 3,
                valueRange = 3.0F..7.0F
            )
        }
    }
}