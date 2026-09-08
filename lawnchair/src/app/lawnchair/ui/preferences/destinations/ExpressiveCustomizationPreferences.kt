package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout

@Composable
fun ExpressiveCustomizationPreferences() {
    PreferenceLayout(title = { androidx.compose.material3.Text("Expressive Customization") }) {
        PreferenceGroup(title = { androidx.compose.material3.Text("Built-in Widgets") }) {
            SwitchPreference(
                adapter = app.lawnchair.preferences.preferenceManager().enableStackedClock.getAdapter(),
                label = "Stacked Clock Widget",
                description = "Display vertical stacked clock on workspace"
            )
            SwitchPreference(
                adapter = app.lawnchair.preferences.preferenceManager().enableWeatherWidget.getAdapter(),
                label = "Weather & Humidity Widget",
                description = "Show weather, humidity, and temperature widget"
            )
            SwitchPreference(
                adapter = app.lawnchair.preferences.preferenceManager().enableQuoteWidget.getAdapter(),
                label = "Quote Card Widget",
                description = "Display daily inspirational quotes"
            )
            SwitchPreference(
                adapter = app.lawnchair.preferences.preferenceManager().enableExpressiveQsb.getAdapter(),
                label = "Expressive Search Bar",
                description = "Multi-shortcut search bar with Lens, AI and Incognito options"
            )
        }

        PreferenceGroup(title = { androidx.compose.material3.Text("Now Brief") }) {
            SwitchPreference(
                adapter = app.lawnchair.preferences.preferenceManager().enableNowBrief.getAdapter(),
                label = "Enable Now Brief Bar",
                description = "Contextual bar for calendar events, media, and quick status (Samsung Now Bar style)"
            )
        }

        PreferenceGroup(title = { androidx.compose.material3.Text("Folders & Icons") }) {
            SwitchPreference(
                adapter = app.lawnchair.preferences.preferenceManager().enableExpressiveFolders.getAdapter(),
                label = "Expressive Folders & Blurred Backgrounds",
                description = "Use custom folder corner radii and card backgrounds"
            )
            SwitchPreference(
                adapter = app.lawnchair.preferences.preferenceManager().enableDualToneIcons.getAdapter(),
                label = "Dual-Tone Contrast Icons",
                description = "Apply high-contrast dual-tone theme glyphs to app icons"
            )
        }
    }
}
