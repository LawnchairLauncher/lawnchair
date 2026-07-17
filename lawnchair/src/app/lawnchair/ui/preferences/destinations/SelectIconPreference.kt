package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.Intent
import android.content.pm.LauncherApps
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.icons.picker.IconPickerItem
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.ui.preferences.navigation.IconPicker
import app.lawnchair.ui.util.OnResult
import app.lawnchair.util.requireSystemService
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import kotlinx.coroutines.launch

private const val TAG = "SelectIconPreference"

@Composable
fun SelectIconPreference(componentKey: ComponentKey) {
    val context = LocalContext.current
    val label = remember(componentKey) {
        resolveAppLabel(context.requireSystemService(), componentKey)
    }
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val launcherAppState = LauncherAppState.getInstance(context)
    val model = launcherAppState.model

    val repo = IconOverrideRepository.INSTANCE.get(context)
    OnResult<IconPickerItem> { item ->
        scope.launch {
            repo.setOverride(componentKey, item)
            (context as Activity).let {
                it.setResult(Activity.RESULT_OK)
                it.finish()
                model.onAppIconChanged(componentKey.componentName.packageName, componentKey.user)
                model.forceReload()
            }
        }
    }

    val overrideItem by repo.observeTarget(componentKey).collectAsStateWithLifecycle(initialValue = null)
    val hasOverride = overrideItem != null

    PreferenceLayoutLazyColumn(label = label) {
        if (hasOverride) {
            preferenceGroupItems(1, isFirstChild = true) {
                ClickablePreference(
                    label = stringResource(id = R.string.icon_picker_reset_to_default),
                    onClick = {
                        scope.launch {
                            repo.deleteOverride(componentKey)
                            (context as Activity).let {
                                it.setResult(Activity.RESULT_OK)
                                it.finish()
                                model.onAppIconChanged(componentKey.componentName.packageName, componentKey.user)
                                model.forceReload()
                            }
                        }
                    },
                )
            }
        }
        preferenceGroupItems(
            items = iconPacks,
            isFirstChild = !hasOverride,
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

/**
 * Resolves a display label for [componentKey], including work/private profiles.
 *
 * [LauncherApps.resolveActivity] can return null or throw for non-current users; fall back to
 * [LauncherApps.getActivityList] and finally the activity class name.
 */
private fun resolveAppLabel(launcherApps: LauncherApps, componentKey: ComponentKey): String {
    val componentName = componentKey.componentName
    val user = componentKey.user
    try {
        val intent = Intent().setComponent(componentName)
        launcherApps.resolveActivity(intent, user)?.label?.toString()?.let { return it }
    } catch (t: Throwable) {
        Log.w(TAG, "resolveActivity failed for $componentKey", t)
    }
    try {
        val activities = launcherApps.getActivityList(componentName.packageName, user)
        activities
            .firstOrNull { it.componentName == componentName }
            ?.label
            ?.toString()
            ?.let { return it }
        activities
            .firstOrNull()
            ?.label
            ?.toString()
            ?.let { return it }
    } catch (t: Throwable) {
        Log.w(TAG, "getActivityList failed for $componentKey", t)
    }
    return componentName.shortClassName?.trimStart('.') ?: componentName.packageName
}
