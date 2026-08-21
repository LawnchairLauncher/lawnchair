package app.lawnchair.drivingmode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupHeading
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.util.App
import app.lawnchair.util.appsState
import app.lawnchair.util.navigationBarsOrDisplayCutoutPadding
import com.android.launcher3.R

/**
 * Long-press menu content for a driving mode button: quick actions (Exit, Settings, Navigation,
 * Music, Phone, Contacts) pinned above the full app list, plus a "Remove" row when the slot
 * already has something assigned.
 */
@Composable
fun DrivingModeButtonPickerContent(
    hasCurrentAssignment: Boolean,
    onSelectApp: (App) -> Unit,
    onSelectSpecial: (DrivingModeSpecialAction) -> Unit,
    onRemove: () -> Unit,
) {
    val apps by appsState()
    Column(
        modifier = Modifier
            .navigationBarsOrDisplayCutoutPadding()
            .fillMaxWidth(),
    ) {
        Text(
            text = stringResource(id = R.string.driving_mode_pick_app_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            if (hasCurrentAssignment) {
                item(key = "remove") {
                    PreferenceTemplate(
                        title = { Text(stringResource(id = R.string.driving_mode_remove_button)) },
                        startWidget = { DrivingModeActionIcon(Icons.Rounded.Close) },
                        onClick = onRemove,
                    )
                }
            }
            item(key = "special_heading") {
                PreferenceGroupHeading(stringResource(id = R.string.driving_mode_special_apps_heading))
            }
            items(DrivingModeSpecialAction.entries, key = { "special_${it.id}" }) { action ->
                PreferenceTemplate(
                    title = { Text(stringResource(id = action.labelRes)) },
                    startWidget = { DrivingModeActionIcon(action.icon) },
                    onClick = { onSelectSpecial(action) },
                )
            }
            item(key = "apps_heading") {
                PreferenceGroupHeading(stringResource(id = R.string.driving_mode_all_apps_heading))
            }
            items(apps, key = { "app_${it.key}" }) { app ->
                AppItem(app = app, onClick = onSelectApp)
            }
        }
    }
}

@Composable
private fun DrivingModeActionIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}
