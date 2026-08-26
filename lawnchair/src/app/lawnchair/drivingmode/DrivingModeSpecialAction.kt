package app.lawnchair.drivingmode

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import com.android.launcher3.R

/** Either a built-in Material icon, or a custom drawable resource (e.g. the speedometer gauge). */
sealed interface DrivingModeIconSource {
    data class Vector(val imageVector: ImageVector) : DrivingModeIconSource
    data class Drawable(@DrawableRes val resId: Int) : DrivingModeIconSource
}

@Composable
fun DrivingModeIconSource.Render(tint: Color, modifier: Modifier = Modifier) {
    when (this) {
        is DrivingModeIconSource.Vector -> Icon(imageVector = imageVector, contentDescription = null, tint = tint, modifier = modifier)
        is DrivingModeIconSource.Drawable -> Image(
            painter = painterResource(resId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = modifier,
        )
    }
}

/**
 * Built-in "quick action" buttons for the driving mode grid, shown at the top of the app picker
 * above the full app list. Persisted as [id] in [app.lawnchair.data.drivingmode.DrivingModeButtonAssignment].
 */
enum class DrivingModeSpecialAction(val id: String, val labelRes: Int, val icon: DrivingModeIconSource) {
    EXIT(
        id = "exit",
        labelRes = R.string.driving_mode_action_exit,
        icon = DrivingModeIconSource.Vector(Icons.AutoMirrored.Rounded.ExitToApp),
    ),
    SETTINGS(
        id = "settings",
        labelRes = R.string.driving_mode_action_settings,
        icon = DrivingModeIconSource.Vector(Icons.Rounded.Settings),
    ),
    NAVIGATION(
        id = "navigation",
        labelRes = R.string.driving_mode_action_navigation,
        icon = DrivingModeIconSource.Vector(Icons.Rounded.Navigation),
    ),
    MUSIC(
        id = "music",
        labelRes = R.string.driving_mode_action_music,
        icon = DrivingModeIconSource.Vector(Icons.Rounded.MusicNote),
    ),
    PHONE(
        id = "phone",
        labelRes = R.string.driving_mode_action_phone,
        icon = DrivingModeIconSource.Vector(Icons.Rounded.Call),
    ),
    CONTACTS(
        id = "contacts",
        labelRes = R.string.driving_mode_action_contacts,
        icon = DrivingModeIconSource.Vector(Icons.Rounded.Person),
    ),
    SPEEDOMETER(
        id = "speedometer",
        labelRes = R.string.driving_mode_action_speedometer,
        icon = DrivingModeIconSource.Drawable(R.drawable.ic_speedometer),
    ),
    ;

    companion object {
        fun fromId(id: String): DrivingModeSpecialAction? = entries.firstOrNull { it.id == id }
    }
}
