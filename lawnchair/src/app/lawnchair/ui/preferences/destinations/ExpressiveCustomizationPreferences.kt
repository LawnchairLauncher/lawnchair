package app.lawnchair.ui.preferences.destinations

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout

@Composable
fun ExpressiveCustomizationPreferences() {
    val prefs = preferenceManager()
    PreferenceLayout(title = { Text(text = "Expressive Customization") }) {
        PreferenceGroup(heading = "Built-in Widgets") {
            SwitchPreference(
                adapter = prefs.enableStackedClock.getAdapter(),
                label = "Stacked Clock Widget",
                description = "Display vertical stacked clock on workspace",
            )
            SwitchPreference(
                adapter = prefs.enableWeatherWidget.getAdapter(),
                label = "Weather & Humidity Widget",
                description = "Show weather, humidity, and temperature widget",
            )
            SwitchPreference(
                adapter = prefs.enableQuoteWidget.getAdapter(),
                label = "Quote Card Widget",
                description = "Display daily inspirational quotes",
            )
            SwitchPreference(
                adapter = prefs.enableExpressiveQsb.getAdapter(),
                label = "Expressive Search Bar",
                description = "Multi-shortcut search bar with Lens, AI and Incognito options",
            )
        }

        PreferenceGroup(heading = "Now Brief") {
            SwitchPreference(
                adapter = prefs.enableNowBrief.getAdapter(),
                label = "Enable Now Brief Bar",
                description = "Contextual bar for calendar events, media, and quick status (Samsung Now Bar style)",
            )
        }

        PreferenceGroup(heading = "Folders & Icons") {
            SwitchPreference(
                adapter = prefs.enableExpressiveFolders.getAdapter(),
                label = "Expressive Folders & Blurred Backgrounds",
                description = "Use custom folder corner radii and card backgrounds",
            )
            SwitchPreference(
                adapter = prefs.enableDualToneIcons.getAdapter(),
                label = "Dual-Tone Contrast Icons",
                description = "Apply high-contrast dual-tone theme glyphs to app icons",
            )
        }
    }
}
