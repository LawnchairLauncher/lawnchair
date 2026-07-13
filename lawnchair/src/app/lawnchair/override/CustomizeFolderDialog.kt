package app.lawnchair.override

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.lawnchair.data.folder.service.FolderService
import app.lawnchair.ui.popup.isFolderBadgeVisible
import app.lawnchair.ui.popup.loadFolderOverrideIcon
import app.lawnchair.ui.popup.setFolderBadgeVisible
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.navigation.SelectFolderIcon
import com.android.launcher3.R
import com.android.launcher3.model.data.FolderInfo

@Composable
fun CustomizeFolderDialog(
    icon: Drawable,
    defaultTitle: String,
    folderId: Int,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var title by remember { mutableStateOf(defaultTitle) }
    var currentIcon by remember { mutableStateOf(icon) }
    val folderService = remember { FolderService.INSTANCE.get(context) }

    // Track if user navigated to icon picker
    var openedIconPicker by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (openedIconPicker) {
            openedIconPicker = false
            loadFolderOverrideIcon(context, folderId)?.let { currentIcon = it }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (title != defaultTitle && title.isNotEmpty()) {
                val folderInfo = FolderInfo().apply {
                    id = folderId
                    this.title = title
                }
                folderService.updateFolderInfoAsync(folderInfo)
            }
        }
    }

    var showBadge by remember {
        mutableStateOf(isFolderBadgeVisible(context, folderId))
    }

    val openIconPicker = {
        focusManager.clearFocus()
        openedIconPicker = true
        val route = SelectFolderIcon(folderId)
        val intent = PreferenceActivity.createIntent(context, route)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    CustomizeDialog(
        icon = currentIcon,
        title = title,
        onTitleChange = { title = it },
        defaultTitle = defaultTitle,
        launchSelectIcon = openIconPicker,
        modifier = modifier,
    ) {
        PreferenceGroup {
            Item {
                SwitchPreference(
                    checked = showBadge,
                    label = stringResource(id = R.string.folder_badge_toggle),
                    onCheckedChange = { newValue ->
                        showBadge = newValue
                        setFolderBadgeVisible(context, folderId, newValue)
                    },
                )
            }
        }
    }
}
