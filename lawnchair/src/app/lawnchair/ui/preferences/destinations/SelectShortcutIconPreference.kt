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
import app.lawnchair.data.iconoverride.ShortcutIconOverrideRepository
import app.lawnchair.icons.picker.CustomIconStore
import app.lawnchair.icons.picker.IconPickerItem
import app.lawnchair.icons.picker.IconType
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.ui.preferences.navigation.IconPicker
import app.lawnchair.ui.util.OnResult
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Icon picker for a single pinned shortcut (e.g. a Gmail label), storing the override in
 * [ShortcutIconOverrideRepository] rather than the per-app [app.lawnchair.data.iconoverride.IconOverrideRepository]
 * used by [SelectIconPreference] — see [app.lawnchair.data.iconoverride.ShortcutIconOverride] for why.
 */
@Composable
fun SelectShortcutIconPreference(shortcutKey: ComponentKey, label: String) {
    val context = LocalContext.current
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context)
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val launcherAppState = LauncherAppState.getInstance(context)
    val model = launcherAppState.model

    val repo = ShortcutIconOverrideRepository.INSTANCE.get(context)

    suspend fun applyItem(item: IconPickerItem) {
        repo.setOverride(shortcutKey, item)
        withContext(Dispatchers.IO) {
            model.onAppIconChanged(shortcutKey.componentName.packageName, shortcutKey.user)
        }
        (context as Activity).let {
            it.setResult(Activity.RESULT_OK)
            it.finish()
        }
    }

    OnResult<IconPickerItem> { item ->
        scope.launch { applyItem(item) }
    }

    // GetContent (rather than PickVisualMedia) opens the system's full "open from" chooser —
    // Files, Downloads, Drive, other file managers — not just the sandboxed Photos picker.
    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val fileName = withContext(Dispatchers.IO) { CustomIconStore.saveIcon(context, uri) }
            if (fileName != null) {
                applyItem(IconPickerItem(packPackageName = "", drawableName = fileName, label = "", type = IconType.Custom))
            } else {
                Toast.makeText(context, R.string.icon_picker_photo_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val overrideItem by repo.observeTarget(shortcutKey).collectAsStateWithLifecycle(initialValue = null)
    val hasOverride = overrideItem != null

    PreferenceLayoutLazyColumn(label = label) {
        if (hasOverride) {
            preferenceGroupItems(1, isFirstChild = true) {
                ClickablePreference(
                    label = stringResource(id = R.string.icon_picker_reset_to_default),
                    onClick = {
                        scope.launch {
                            repo.deleteOverride(shortcutKey)
                            withContext(Dispatchers.IO) {
                                model.onAppIconChanged(
                                    shortcutKey.componentName.packageName,
                                    shortcutKey.user,
                                )
                            }
                            (context as Activity).let {
                                it.setResult(Activity.RESULT_OK)
                                it.finish()
                            }
                        }
                    },
                )
            }
        }
        preferenceGroupItems(1, isFirstChild = !hasOverride) {
            ClickablePreference(
                label = stringResource(id = R.string.icon_picker_choose_photo),
                onClick = {
                    photoPickerLauncher.launch("image/*")
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
                    mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
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
