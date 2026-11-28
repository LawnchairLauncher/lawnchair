package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.folder.FolderIconOverride
import app.lawnchair.icons.picker.IconPickerItem
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.ui.preferences.navigation.IconPicker
import app.lawnchair.ui.util.OnResult
import com.android.launcher3.R
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import kotlinx.coroutines.launch

/**
 * LC: picks an icon pack icon for a home screen folder, shown instead of the folder's preview.
 * Works like picking a custom app icon.
 */
@Composable
fun SelectFolderIconPreference(
    itemId: Int,
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val msdlPlayer = MSDLPlayerWrapper.INSTANCE.get(context)
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val prefs = PreferenceManager2.getInstance(context)

    fun finish() {
        (context as Activity).let {
            it.setResult(Activity.RESULT_OK)
            it.finish()
        }
    }

    OnResult<IconPickerItem> { item ->
        scope.launch {
            prefs.setFolderIcon(itemId, FolderIconOverride.from(item))
            finish()
        }
    }

    val current by remember(itemId) { prefs.getFolderIcon(itemId) }.collectAsStateWithLifecycle(initialValue = null)
    val hasIcon = current != null

    PreferenceLayoutLazyColumn(
        label = title.ifEmpty { stringResource(id = R.string.folder_change_icon) },
        modifier = modifier,
    ) {
        if (hasIcon) {
            preferenceGroupItems(1, isFirstChild = true) {
                ClickablePreference(
                    label = stringResource(id = R.string.icon_picker_reset_to_default),
                    onClick = {
                        scope.launch {
                            prefs.setFolderIcon(itemId, null)
                            finish()
                        }
                    },
                )
            }
        }
        preferenceGroupItems(
            items = iconPacks,
            isFirstChild = !hasIcon,
            heading = { stringResource(id = R.string.pick_icon_from_label) },
        ) { _, iconPack ->
            AppItem(
                label = iconPack.name,
                icon = remember(iconPack) { iconPack.icon.toBitmap() },
                onClick = {
                    msdlPlayer.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                    navController.navigate(IconPicker(iconPack.packageName))
                },
            )
        }
    }
}
