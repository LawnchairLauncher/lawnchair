package app.lawnchair.ui.preferences.destinations

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.predictions.LawnchairPredictionManager
import app.lawnchair.predictions.PredictionAppKey
import app.lawnchair.ui.OverflowMenuGrouped
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.AppItemPlaceholder
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.util.App
import app.lawnchair.util.appComparator
import app.lawnchair.util.appsState
import com.android.launcher3.R
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ComponentKey
import java.util.Comparator.comparing

@Composable
fun DismissedPredictionAppsPreferences(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val userCache = UserCache.INSTANCE.get(context)
    val dismissedAppsStore = remember {
        LawnchairPredictionManager.getInstance(context).dismissedAppsStore
    }

    var dismissedApps by remember {
        mutableStateOf(dismissedAppsStore.getEntries().toSet())
    }
    val dismissedComponentKeys = remember(dismissedApps) {
        dismissedApps.mapNotNull { key ->
            val info = PredictionAppKey.parse(key) ?: return@mapNotNull null
            val serial = info.userToken.toLongOrNull() ?: return@mapNotNull null
            val user = userCache.getUserForSerialNumber(serial) ?: return@mapNotNull null
            ComponentKey(ComponentName(info.packageName, info.className), user)
        }.toSet()
    }

    val userSerials = remember(context) {
        userCache.userProfiles.associateWith { userCache.getSerialNumberForUser(it).toString() }
    }

    val apps by appsState(comparator = dismissedPredictionAppsComparator(dismissedComponentKeys))

    PreferenceScaffold(
        label = if (dismissedApps.isEmpty()) {
            stringResource(R.string.dismissed_prediction_apps_label)
        } else {
            stringResource(R.string.dismissed_prediction_apps_label_with_count, dismissedApps.size)
        },
        actions = {
            if (dismissedApps.isNotEmpty()) {
                ResetDismissedAppsAction(onReset = {
                    dismissedAppsStore.setEntries(emptySet())
                    dismissedApps = emptySet()
                })
            }
        },
        modifier = modifier,
        isExpandedScreen = LocalIsExpandedScreen.current,
    ) {
        Crossfade(targetState = apps.isNotEmpty(), label = "") { present ->
            if (present) {
                PreferenceLazyColumn(it, state = rememberLazyListState()) {
                    val toggleDismissedApp = { app: App ->
                        val userSerial = userSerials[app.key.user] ?: userCache.getSerialNumberForUser(app.key.user).toString()
                        val key = PredictionAppKey.create(app.key.componentName, userSerial)
                        if (!dismissedApps.contains(key)) {
                            dismissedAppsStore.add(key)
                        } else {
                            dismissedAppsStore.remove(key)
                        }
                        dismissedApps = dismissedAppsStore.getEntries().toSet()
                    }
                    preferenceGroupItems(
                        items = apps,
                        isFirstChild = true,
                    ) { _, app ->
                        AppItem(
                            app = app,
                            onClick = toggleDismissedApp,
                        ) {
                            Checkbox(
                                checked = app.key in dismissedComponentKeys,
                                onCheckedChange = null,
                            )
                        }
                    }
                }
            } else {
                PreferenceLazyColumn(it, enabled = false) {
                    preferenceGroupItems(
                        count = 20,
                        isFirstChild = true,
                    ) {
                        AppItemPlaceholder {
                            Spacer(Modifier.width(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResetDismissedAppsAction(
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OverflowMenuGrouped(modifier) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShape(0, 1),
        ) {
            DropdownMenuItem(
                onClick = {
                    onReset()
                    hideMenu()
                },
                text = {
                    Text(stringResource(R.string.action_reset))
                },
            )
        }
    }
}

@Composable
private fun dismissedPredictionAppsComparator(dismissedComponentKeys: Set<ComponentKey>) = remember(dismissedComponentKeys) {
    Comparator<App> { a, b ->
        val aDismissed = a.key in dismissedComponentKeys
        val bDismissed = b.key in dismissedComponentKeys
        when {
            aDismissed == bDismissed -> appComparator.compare(a, b)
            aDismissed -> -1
            else -> 1
        }
    }
}
