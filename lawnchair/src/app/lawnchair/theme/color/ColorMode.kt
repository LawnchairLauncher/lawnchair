package app.lawnchair.theme.color

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import com.android.launcher3.R

enum class ColorMode(
    @StringRes val labelResourceId: Int,
) {
    AUTO(
        labelResourceId = R.string.managed_by_lawnchair,
    ),
    LIGHT(
        labelResourceId = R.string.color_light,
    ),
    DARK(
        labelResourceId = R.string.color_dark,
    ),
    ;

    companion object {
        fun values() = listOf(
            AUTO,
            LIGHT,
            DARK,
        )

        fun fromString(string: String) = values().firstOrNull { it.toString() == string }

        fun entries(): List<ListPreferenceEntry<ColorMode>> = values().map {
            ListPreferenceEntry(value = it) { stringResource(id = it.labelResourceId) }
        }
    }
}
