package app.lawnchair.ui.preferences

import android.app.Activity
import android.content.Intent
import android.content.pm.LauncherApps
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.icons.IconPickerItem
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.ClickablePreference
import app.lawnchair.ui.preferences.components.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.preferenceGroupItems
import app.lawnchair.ui.util.OnResult
import app.lawnchair.util.requireSystemService
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import com.google.accompanist.navigation.animation.composable
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.selectIconGraph(route: String) {
    preferenceGraph(route, { }) { subRoute ->
        composable(
            route = subRoute("{packageName}/{nameAndUser}"),
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType },
                navArgument("nameAndUser") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments!!
            val packageName = args.getString("packageName")
            val nameAndUser = args.getString("nameAndUser")
            val key = ComponentKey.fromString("$packageName/$nameAndUser")!!
            SelectIconPreference(key)
        }
    }
}

@Composable
fun SelectIconPreference(componentKey: ComponentKey) {
    val context = LocalContext.current
    val label = remember(componentKey) {
        val launcherApps: LauncherApps = context.requireSystemService()
        val intent = Intent().setComponent(componentKey.componentName)
        val activity = launcherApps.resolveActivity(intent, componentKey.user)
        activity.label.toString()
    }
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsState()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val repo = IconOverrideRepository.INSTANCE.get(context)
    OnResult<IconPickerItem> { item ->
        scope.launch {
            repo.setOverride(componentKey, item)
            (context as Activity).let {
                it.setResult(Activity.RESULT_OK)
                it.finish()
            }
        }
    }

    val overrideItem by repo.observeTarget(componentKey).collectAsState(initial = null)
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
                            }
                        }
                    }
                )
            }
        }
        preferenceGroupItems(
            heading = { stringResource(id = R.string.pick_icon_from_label) },
            items = iconPacks,
            isFirstChild = !hasOverride
        ) { _, iconPack ->
            AppItem(
                label = iconPack.name,
                icon = remember(iconPack) { iconPack.icon.toBitmap() },
                onClick = {
                    if (iconPack.packageName.isEmpty()) {
                        navController.navigate("/${Routes.ICON_PICKER}/")
                    } else {
                        navController.navigate("/${Routes.ICON_PICKER}/${iconPack.packageName}/")
                    }
                }
            )
        }
    }
}
