@file:Suppress("SYNTHETIC_PROPERTY_WITHOUT_JAVA_ORIGIN")

package app.lawnchair.ui.preferences.components.folder

import android.annotation.SuppressLint
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lawnchair.LawnchairLauncher
import app.lawnchair.data.factory.ViewModelFactory
import app.lawnchair.data.folder.model.FolderViewModel
import app.lawnchair.launcher
import app.lawnchair.launcherNullable
import app.lawnchair.preferences2.ReloadHelper
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.AppItemPlaceholder
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.util.sortedBySelection
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo

@SuppressLint("MutableCollectionMutableState")
@Composable
fun AppListToFolderPreferences(
    folderInfoId: Int?,
    modifier: Modifier = Modifier,
) {
    if (folderInfoId == null) return

    val context = LocalContext.current
    val launcher = context.launcherNullable ?: LawnchairLauncher.instance?.launcher
    if (launcher == null) return

    val reloadHelper = ReloadHelper(launcher)

    val viewModel: FolderViewModel = viewModel(factory = ViewModelFactory(launcher) { FolderViewModel(it) })

    val folderInfo by viewModel.folderInfo.collectAsState()
    val loading = folderInfo == null

    val selectedAppsState = remember { mutableStateOf(setOf<ItemInfo>()) }

    LaunchedEffect(folderInfoId) {
        viewModel.setFolderInfo(folderInfoId, false)
    }

    LaunchedEffect(folderInfo) {
        val folderContents = folderInfo?.contents?.toMutableSet() ?: mutableSetOf()
        selectedAppsState.value = folderContents
    }

    val apps = launcher.mAppsView.appsStore.apps.toList()
        .sortedBySelection(selectedAppsState.value)

    val state = rememberLazyListState()

    val label = if (loading) {
        "Loading..."
    } else {
        folderInfo?.title.toString() + " (" + selectedAppsState.value.size + ")"
    }
    PreferenceScaffold(
        label = label,
        modifier = modifier,
        isExpandedScreen = LocalIsExpandedScreen.current,
    ) {
        Crossfade(targetState = loading, label = "") { isLoading ->
            if (isLoading) {
                PreferenceLazyColumn(it, enabled = false, state = state) {
                    preferenceGroupItems(
                        count = 20,
                        isFirstChild = true,
                        dividerStartIndent = 40.dp,
                    ) {
                        AppItemPlaceholder {
                            Spacer(Modifier.width(24.dp))
                        }
                    }
                }
            } else {
                PreferenceLazyColumn(it, state = state) {
                    val updateFolderApp = { app: AppInfo ->
                        val updatedSelectedApps = selectedAppsState.value.toMutableSet().apply {
                            val isChecked = any { it is AppInfo && it.targetPackage == app.targetPackage }
                            if (isChecked) {
                                removeIf { it is AppInfo && it.targetPackage == app.targetPackage }
                            } else {
                                add(app)
                            }
                        }

                        selectedAppsState.value = updatedSelectedApps

                        viewModel.updateFolderWithItems(
                            folderInfoId,
                            folderInfo?.title.toString(),
                            updatedSelectedApps.filterIsInstance<AppInfo>().toList(),
                        )
                        reloadHelper.reloadGrid()
                    }

                    preferenceGroupItems(apps, isFirstChild = true, dividerStartIndent = 40.dp) { _, app ->
                        key(app.toString()) {
                            AppItem(app, onClick = { updateFolderApp(app) }) {
                                Checkbox(
                                    checked = selectedAppsState.value.any {
                                        val appInfo = it as? AppInfo
                                        appInfo?.targetPackage == app.targetPackage
                                    },
                                    onCheckedChange = { isChecked ->
                                        updateFolderApp(app)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
