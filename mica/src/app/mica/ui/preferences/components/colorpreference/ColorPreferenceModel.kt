package app.mica.ui.preferences.components.colorpreference

import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import app.mica.theme.color.ColorOption
import com.patrykmichalik.opto.domain.Preference

data class ColorPreferenceModel(
    val prefObject: Preference<ColorOption, String, Preferences.Key<String>>,
    @StringRes val labelRes: Int,
    val dynamicEntries: List<ColorPreferenceEntry<ColorOption>>,
)
