package app.lawnchair.animation

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import com.android.launcher3.R

enum class FolderAnimationSpeed(
    /** Delta to apply to base duration in milliseconds */
    val durationDelta: Int,
    @StringRes val labelResourceId: Int,
) {
    SLOW(
        durationDelta = 100,
        labelResourceId = R.string.folder_animation_speed_slow,
    ),
    NORMAL(
        durationDelta = 0,
        labelResourceId = R.string.folder_animation_speed_default,
    ),
    FAST(
        durationDelta = -100,
        labelResourceId = R.string.folder_animation_speed_fast,
    ),
    ;

    companion object {
        fun values() = listOf(
            SLOW,
            NORMAL,
            FAST,
        )

        fun fromString(string: String) = values().firstOrNull { it.name.equals(string, ignoreCase = true) }
            ?: if (string.equals("DEFAULT", ignoreCase = true)) NORMAL else null

        fun entries(): List<ListPreferenceEntry<FolderAnimationSpeed>> = values().map {
            ListPreferenceEntry(value = it) { stringResource(id = it.labelResourceId) }
        }
    }
}
