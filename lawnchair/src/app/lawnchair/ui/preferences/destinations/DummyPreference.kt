package app.lawnchair.ui.preferences.destinations

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate

@Composable
fun DummyPreference() {
    PreferenceLayout(
        label = "",
        backArrowVisible = false
    ) {
        PreferenceTemplate(title = {
            Text("Tap a preference in the left hand menu")
        })
    }
}
