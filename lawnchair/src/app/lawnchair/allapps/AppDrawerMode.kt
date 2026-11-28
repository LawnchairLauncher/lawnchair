package app.lawnchair.allapps

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import com.android.launcher3.R

enum class AppDrawerMode(
    @StringRes val labelResourceId: Int,
) {
    VERTICAL(
        labelResourceId = R.string.app_drawer_mode_vertical,
    ),
    HORIZONTAL(
        labelResourceId = R.string.app_drawer_mode_horizontal,
    ),
    ;

    override fun toString(): String = when (this) {
        VERTICAL -> "vertical"
        HORIZONTAL -> "horizontal"
    }

    companion object {
        fun values() = listOf(
            VERTICAL,
            HORIZONTAL,
        )

        fun fromString(string: String?): AppDrawerMode = values().firstOrNull { it.toString() == string } ?: VERTICAL

        fun entries(): List<ListPreferenceEntry<AppDrawerMode>> = values().map {
            ListPreferenceEntry(value = it) { stringResource(id = it.labelResourceId) }
        }
    }
}
