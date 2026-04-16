package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.icons.picker.IconPickerItem
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.ui.popup.invalidateFolderIconCache
import app.lawnchair.ui.popup.saveGalleryIconToPrivateStorage
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.ui.preferences.navigation.IconPicker
import app.lawnchair.ui.util.OnResult
import com.android.launcher3.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SelectFolderIconPreference(folderId: Int) {
    val context = LocalContext.current
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferenceManager.getInstance(context) }

    val hasOverride = prefs.folderCustomIcon[folderId] != null

    OnResult<IconPickerItem> { item ->
        scope.launch {
            val serialized = "${item.packPackageName}|${item.drawableName}|${item.label}|${item.type.name}"
            prefs.folderCustomIcon[folderId] = serialized
            invalidateFolderIconCache(folderId)
            (context as? Activity)?.let {
                it.setResult(Activity.RESULT_OK)
                it.finish()
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val filePath = withContext(Dispatchers.IO) {
                    saveGalleryIconToPrivateStorage(context, folderId, uri)
                        ?: throw IllegalStateException("Cannot save image")
                }
                prefs.folderCustomIcon[folderId] = filePath
                invalidateFolderIconCache(folderId)
                (context as? Activity)?.let {
                    it.setResult(Activity.RESULT_OK)
                    it.finish()
                }
            } catch (e: Exception) {
                Toast.makeText(context, R.string.failed_to_load_icon, Toast.LENGTH_SHORT).show()
            }
        }
    }

    PreferenceLayoutLazyColumn(label = stringResource(R.string.action_customize)) {
        if (hasOverride) {
            preferenceGroupItems(1, isFirstChild = true) {
                ClickablePreference(
                    label = stringResource(id = R.string.icon_picker_reset_to_default),
                    onClick = {
                        scope.launch {
                            app.lawnchair.ui.popup.folderIconFile(context, folderId).delete()
                            prefs.folderCustomIcon[folderId] = null
                            invalidateFolderIconCache(folderId)
                            (context as? Activity)?.let {
                                it.setResult(Activity.RESULT_OK)
                                it.finish()
                            }
                        }
                    },
                )
            }
        }
        // Pick from gallery
        preferenceGroupItems(1, isFirstChild = !hasOverride) {
            ClickablePreference(
                label = stringResource(id = R.string.pick_icon_from_gallery),
                onClick = {
                    galleryLauncher.launch(
                        android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                            type = "image/*"
                            addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        },
                    )
                },
            )
        }
        preferenceGroupItems(
            items = iconPacks,
            isFirstChild = false,
            heading = { stringResource(id = R.string.pick_icon_from_label) },
        ) { _, iconPack ->
            AppItem(
                label = iconPack.name,
                icon = remember(iconPack) { iconPack.icon.toBitmap() },
                onClick = {
                    if (iconPack.packageName.isEmpty()) {
                        navController.navigate(IconPicker())
                    } else {
                        navController.navigate(IconPicker(iconPack.packageName))
                    }
                },
            )
        }
    }
}
