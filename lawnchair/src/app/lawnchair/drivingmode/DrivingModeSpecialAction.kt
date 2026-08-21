package app.lawnchair.drivingmode

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.launcher3.R

/**
 * Built-in "quick action" buttons for the driving mode grid, shown at the top of the app picker
 * above the full app list. Persisted as [id] in [app.lawnchair.data.drivingmode.DrivingModeButtonAssignment].
 */
enum class DrivingModeSpecialAction(val id: String, val labelRes: Int, val icon: ImageVector) {
    EXIT(
        id = "exit",
        labelRes = R.string.driving_mode_action_exit,
        icon = Icons.AutoMirrored.Rounded.ExitToApp,
    ),
    SETTINGS(
        id = "settings",
        labelRes = R.string.driving_mode_action_settings,
        icon = Icons.Rounded.Settings,
    ),
    NAVIGATION(
        id = "navigation",
        labelRes = R.string.driving_mode_action_navigation,
        icon = Icons.Rounded.Navigation,
    ),
    MUSIC(
        id = "music",
        labelRes = R.string.driving_mode_action_music,
        icon = Icons.Rounded.MusicNote,
    ),
    PHONE(
        id = "phone",
        labelRes = R.string.driving_mode_action_phone,
        icon = Icons.Rounded.Call,
    ),
    CONTACTS(
        id = "contacts",
        labelRes = R.string.driving_mode_action_contacts,
        icon = Icons.Rounded.Person,
    ),
    ;

    companion object {
        fun fromId(id: String): DrivingModeSpecialAction? = entries.firstOrNull { it.id == id }
    }
}
