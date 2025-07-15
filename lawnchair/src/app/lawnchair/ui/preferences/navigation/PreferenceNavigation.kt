package app.lawnchair.ui.preferences.navigation

import androidx.collection.intSetOf
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import app.lawnchair.backup.ui.CreateBackupScreen
import app.lawnchair.backup.ui.RestoreBackupScreen
import app.lawnchair.backup.ui.RestoreBackupViewModel
import app.lawnchair.icons.IconPack
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.preferences.BasePreferenceManager
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.LocalBackStack
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.about.About
import app.lawnchair.ui.preferences.about.acknowledgements.Acknowledgements
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreferenceModelList
import app.lawnchair.ui.preferences.components.colorpreference.ColorSelection
import app.lawnchair.ui.preferences.destinations.AppDrawerFoldersPreference
import app.lawnchair.ui.preferences.destinations.AppDrawerPreferences
import app.lawnchair.ui.preferences.destinations.CustomIconShapePreference
import app.lawnchair.ui.preferences.destinations.DebugMenuPreferences
import app.lawnchair.ui.preferences.destinations.DockPreferences
import app.lawnchair.ui.preferences.destinations.DummyPreference
import app.lawnchair.ui.preferences.destinations.ExperimentalFeaturesPreferences
import app.lawnchair.ui.preferences.destinations.FolderPreferences
import app.lawnchair.ui.preferences.destinations.FontSelection
import app.lawnchair.ui.preferences.destinations.GeneralPreferences
import app.lawnchair.ui.preferences.destinations.GesturePreferences
import app.lawnchair.ui.preferences.destinations.HiddenAppsPreferences
import app.lawnchair.ui.preferences.destinations.HomeScreenGridPreferences
import app.lawnchair.ui.preferences.destinations.HomeScreenPreferences
import app.lawnchair.ui.preferences.destinations.IconPackPreferences
import app.lawnchair.ui.preferences.destinations.IconPickerPreference
import app.lawnchair.ui.preferences.destinations.IconShapePreference
import app.lawnchair.ui.preferences.destinations.LauncherPopupPreference
import app.lawnchair.ui.preferences.destinations.PickAppForGesture
import app.lawnchair.ui.preferences.destinations.PreferencesDashboard
import app.lawnchair.ui.preferences.destinations.QuickstepPreferences
import app.lawnchair.ui.preferences.destinations.SearchPreferences
import app.lawnchair.ui.preferences.destinations.SearchProviderPreferences
import app.lawnchair.ui.preferences.destinations.SelectAppsForDrawerFolder
import app.lawnchair.ui.preferences.destinations.SelectIconPreference
import app.lawnchair.ui.preferences.destinations.SmartspacePreferences
import com.android.launcher3.util.ComponentKey
import java.util.Base64
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut
import soup.compose.material.motion.animation.rememberSlideDistance

@Composable
fun PreferenceNavigation(
    startDestination: PreferenceRoute,
) {
    val backStack = LocalBackStack.current

//    if (backStack.isEmpty()) //?
    backStack.add(startDestination)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        contentAlignment = Alignment.Center,
        transitionSpec = {
            // Slide in from right when navigating forward
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = {
            // Slide in from left when navigating back
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
        predictivePopTransitionSpec = {
            // Slide in from left when navigating back
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
        entryProvider = { key ->
            NavEntry(key) {
                when (key) {
                    is Root -> {
                        val isExpandedScreen = LocalIsExpandedScreen.current

                        PreferencesDashboard(
                            currentRoute = Root,
                            onNavigate = {
                                backStack.add(it)
                            },
                        )

                        LaunchedEffect(isExpandedScreen) {
                            if (isExpandedScreen) {
                                backStack.add(General)

//                                    launchSingleTop = true
//                                    popUpTo(navController.graph.id)

                            }
                        }
                    }

                    is Dummy -> DummyPreference()

                    is General -> GeneralPreferences()
                    is GeneralFontSelection -> {
                        val pref = preferenceManager().prefsMap[key.prefKey]
                            as? BasePreferenceManager.FontPref ?: return@NavEntry
                        FontSelection(pref)
                    }

                    is GeneralIconPack -> IconPackPreferences()
                    is GeneralIconShape -> IconShapePreference()
                    is GeneralCustomIconShapeCreator -> CustomIconShapePreference()

                    is HomeScreen -> HomeScreenPreferences()
                    is HomeScreenGrid -> HomeScreenGridPreferences()
                    is HomeScreenPopupEditor -> LauncherPopupPreference()

                    is Dock -> DockPreferences()
                    is DockSearchProvider -> SearchProviderPreferences()

                    is Smartspace -> SmartspacePreferences(fromWidget = false)
                    is SmartspaceWidget -> SmartspacePreferences(fromWidget = true)

                    is AppDrawer -> AppDrawerPreferences()
                    is AppDrawerHiddenApps -> HiddenAppsPreferences()
                    is AppDrawerAppListToFolder -> SelectAppsForDrawerFolder(key.id)
                    is AppDrawerFolder -> AppDrawerFoldersPreference()

                    is Search -> SearchPreferences(currentTab = key.selectedId)

                    is Folders -> FolderPreferences()

                    is Gestures -> GesturePreferences()
                    is GesturesPickApp -> PickAppForGesture()

                    is Quickstep -> QuickstepPreferences()

                    is About -> About()
                    is AboutLicenses -> Acknowledgements()

                    is DebugMenu -> DebugMenuPreferences()

                    is SelectIcon -> SelectIconPreference(ComponentKey.fromString(key.componentKey)!!)
                    is IconPicker -> IconPickerPreference(packageName = key.packageName)

                    is ExperimentalFeatures -> ExperimentalFeaturesPreferences()

                    is ColorSelection -> {
                        val modelList = ColorPreferenceModelList.INSTANCE.get(LocalContext.current)
                        val model = modelList[key.prefKey]
                        ColorSelection(
                            label = stringResource(id = model.labelRes),
                            preference = model.prefObject,
                            dynamicEntries = model.dynamicEntries,
                        )
                    }

                    is CreateBackup -> CreateBackupScreen(viewModel())
                    is RestoreBackup -> {
                        val backupUri = remember {
                            val base64Uri = key.base64Uri
                            val backupUriString = String(Base64.getDecoder().decode(base64Uri))
                            backupUriString.toUri()
                        }
                        val viewModel: RestoreBackupViewModel = viewModel()
                        DisposableEffect(key1 = null) {
                            viewModel.init(backupUri)
                            onDispose { }
                        }
                        RestoreBackupScreen()
                    }
                }
            }
        },
    )
}
