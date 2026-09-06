package app.lawnchair.ui.preferences.components.search

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.not
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.qsb.LawnQsbLayout
import app.lawnchair.qsb.LawnQsbUi
import app.lawnchair.qsb.QsbActions
import app.lawnchair.qsb.buildQsbStyle
import app.lawnchair.qsb.providers.Google
import app.lawnchair.qsb.providers.PixelSearch
import app.lawnchair.qsb.providers.QsbSearchProvider
import app.lawnchair.qsb.rememberAllAppsQsbState
import app.lawnchair.search.algorithms.LawnchairSearchAlgorithm
import app.lawnchair.search.algorithms.engine.provider.web.CustomWebSearchProvider
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.tokens.ColorTokens
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.HiddenAppsInSearchPreference
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.controls.TwoTargetSwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.navigation.SearchProviderPreference
import app.lawnchair.ui.theme.preferenceGroupColor
import app.lawnchair.util.FileAccessManager
import com.android.launcher3.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@Composable
fun DrawerSearchPreference(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val context = LocalContext.current

    val showDrawerSearchBar = !prefs2.hideAppDrawerSearchBar.getAdapter()
    val hiddenApps = prefs2.hiddenApps.getAdapter().state.value

    val drawerQsbCornerRadius = prefs.drawerQsbCornerRadius.getAdapter()
    val drawerQsbAlpha = prefs.drawerQsbAlpha.getAdapter()
    val drawerQsbStrokeWidth = prefs.drawerQsbStrokeWidth.getAdapter()
    val strokeColorStyle = prefs2.strokeColorStyle.getAdapter()

    MainSwitchPreference(
        adapter = showDrawerSearchBar,
        label = stringResource(id = R.string.show_app_search_bar),
        modifier = modifier,
    ) {
        DrawerSearchBarPreview(
            provider = prefs2.hotseatQsbProvider.getAdapter().state.value,
            themed = prefs2.themedHotseatQsb.getAdapter().state.value,
            showIcons = prefs2.matchHotseatQsbStyle.getAdapter().state.value,
            cornerRadiusFactor = drawerQsbCornerRadius.state.value,
            backgroundAlpha = drawerQsbAlpha.state.value,
            strokeWidth = drawerQsbStrokeWidth.state.value,
            strokeColor = strokeColorStyle.state.value,
        )
        PreferenceGroup(heading = stringResource(R.string.general_label)) {
            if (hiddenApps.isNotEmpty()) {
                HiddenAppsInSearchPreference()
            }
            SwitchPreference(
                adapter = prefs2.autoShowKeyboardInDrawer.getAdapter(),
                label = stringResource(id = R.string.pref_search_auto_show_keyboard),
            )
            SearchProvider(
                context = context,
            )
            SwitchPreference(
                label = stringResource(R.string.allapps_match_qsb_style_label),
                description = stringResource(R.string.allapps_match_qsb_style_description),
                adapter = prefs2.matchHotseatQsbStyle.getAdapter(),
            )
        }

        PreferenceGroup(heading = stringResource(R.string.style)) {
            SliderPreference(
                label = stringResource(id = R.string.corner_radius_label),
                adapter = drawerQsbCornerRadius,
                step = 0.05F,
                valueRange = 0F..1F,
                showAsPercentage = true,
            )
            SliderPreference(
                label = stringResource(id = R.string.background_opacity),
                adapter = drawerQsbAlpha,
                step = 5,
                valueRange = 0..100,
                showUnit = "%",
            )
            SliderPreference(
                label = stringResource(id = R.string.qsb_hotseat_stroke_width),
                adapter = drawerQsbStrokeWidth,
                step = 1f,
                valueRange = 0f..10f,
                showUnit = "vw",
            )
            if (drawerQsbStrokeWidth.state.value > 0f) {
                ColorPreference(preference = prefs2.strokeColorStyle)
            }
        }

        val searchAlgorithm = preferenceManager2().searchAlgorithm.getAdapter().state.value
        val navController = LocalNavController.current
        PreferenceGroup(heading = stringResource(id = R.string.show_search_result_types)) {
            if (searchAlgorithm != LawnchairSearchAlgorithm.ASI_SEARCH) {
                val canDisable = searchAlgorithm != LawnchairSearchAlgorithm.APP_SEARCH
                val adapter = prefs.searchResultApps.getAdapter()

                TwoTargetSwitchPreference(
                    checked = if (canDisable) adapter.state.value else true,
                    onCheckedChange = if (canDisable) adapter::onChange else ({}),
                    enabled = canDisable,
                    label = stringResource(R.string.search_pref_result_apps_and_shortcuts_title),
                    onClick = {
                        navController.navigate(SearchProviderPreference(SearchProviderId.APPS))
                    },
                )
            }
            when (searchAlgorithm) {
                LawnchairSearchAlgorithm.LOCAL_SEARCH -> {
                    LocalSearchSettings(
                        prefs = prefs,
                        prefs2 = prefs2,
                        context = context,
                    )
                }

                LawnchairSearchAlgorithm.ASI_SEARCH -> {
                    ASISearchSettings(prefs)
                }
            }
        }
    }
}

@Composable
private fun ASISearchSettings(prefs: PreferenceManager) {
    SwitchPreference(
        adapter = prefs.searchResultShortcuts.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_shortcuts_title),
    )
    SwitchPreference(
        adapter = prefs.searchResultPeople.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_people_title),
    )
    SwitchPreference(
        adapter = prefs.searchResultPixelTips.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_tips_title),
    )
    SwitchPreference(
        adapter = prefs.searchResultSettings.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_settings_title),
    )
}

@Composable
private fun SearchProvider(
    context: Context,
) {
    val searchAlgorithmEntries = remember {
        sequenceOf(
            ListPreferenceEntry(LawnchairSearchAlgorithm.APP_SEARCH) { stringResource(R.string.search_algorithm_app_search) },
            ListPreferenceEntry(LawnchairSearchAlgorithm.LOCAL_SEARCH) { stringResource(R.string.search_algorithm_global_search_on_device) },
            ListPreferenceEntry(LawnchairSearchAlgorithm.ASI_SEARCH) { stringResource(R.string.search_algorithm_global_search_via_asi) },
        ).filter {
            when (it.value) {
                LawnchairSearchAlgorithm.ASI_SEARCH -> LawnchairSearchAlgorithm.isASISearchEnabled(
                    context,
                )

                else -> true
            }
        }.toList()
    }

    ListPreference(
        adapter = preferenceManager2().searchAlgorithm.getAdapter(),
        entries = searchAlgorithmEntries,
        label = stringResource(R.string.app_search_algorithm),
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun LocalSearchSettings(
    prefs: PreferenceManager,
    prefs2: PreferenceManager2,
    context: Context,
) {
    val navController = LocalNavController.current
    val webSuggestionProvider =
        stringResource(prefs2.webSuggestionProvider.getAdapter().state.value.label)

    TwoTargetSwitchPreference(
        adapter = prefs.searchResultStartPageSuggestion.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_web_title),
        description = if (webSuggestionProvider == stringResource(CustomWebSearchProvider.label)) {
            webSuggestionProvider
        } else {
            stringResource(
                id = R.string.search_pref_result_web_provider_description,
                webSuggestionProvider,
            )
        },
        onClick = {
            navController.navigate(SearchProviderPreference(SearchProviderId.WEB))
        },
    )
    val peopleAdapter = prefs.searchResultPeople.getAdapter()
    val peopleEnabled = rememberPermissionState(android.Manifest.permission.READ_CONTACTS).status.isGranted
    TwoTargetSwitchPreference(
        checked = peopleEnabled && peopleAdapter.state.value,
        onCheckedChange = peopleAdapter::onChange,
        switchEnabled = peopleEnabled,
        label = stringResource(id = R.string.search_pref_result_people_title),
        description = stringResource(id = R.string.search_pref_result_contacts_description),
        onClick = {
            navController.navigate(SearchProviderPreference(SearchProviderId.CONTACTS))
        },
    )
    val filesAdapter = prefs.searchResultFilesToggle.getAdapter()
    val filesEnabled = remember { FileAccessManager.getInstance(context) }.hasAnyPermission.collectAsStateWithLifecycle().value
    TwoTargetSwitchPreference(
        checked = filesEnabled && filesAdapter.state.value,
        onCheckedChange = filesAdapter::onChange,
        switchEnabled = filesEnabled,
        label = stringResource(R.string.search_pref_result_files_title),
        description = stringResource(R.string.search_pref_result_files_description),
        onClick = {
            navController.navigate(SearchProviderPreference(SearchProviderId.FILES))
        },
    )
    TwoTargetSwitchPreference(
        adapter = prefs.searchResultSettingsEntry.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_settings_title),
        onClick = {
            navController.navigate(SearchProviderPreference(SearchProviderId.SETTINGS))
        },
    )
    TwoTargetSwitchPreference(
        adapter = prefs.searchResulRecentSuggestion.getAdapter(),
        label = stringResource(id = R.string.search_pref_result_history_title),
        onClick = {
            navController.navigate(SearchProviderPreference(SearchProviderId.HISTORY))
        },
    )
    SwitchPreference(
        adapter = prefs.searchResultCalculator.getAdapter(),
        label = stringResource(R.string.all_apps_search_result_calculator),
    )
}

/** Shows the drawer search bar as the settings above it describe it. */
@Composable
private fun DrawerSearchBarPreview(
    provider: QsbSearchProvider,
    themed: Boolean,
    showIcons: Boolean,
    cornerRadiusFactor: Float,
    backgroundAlpha: Int,
    strokeWidth: Float,
    strokeColor: ColorOption,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val supportsLens = provider == Google || provider == PixelSearch
    val voiceIntent = remember(provider, context) { LawnQsbLayout.getVoiceIntent(provider, context) }
    val lensIntent = remember(supportsLens, context) {
        if (supportsLens) LawnQsbLayout.getLensIntent(context) else null
    }

    PreferenceGroup(heading = stringResource(id = R.string.preview_label)) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(color = preferenceGroupColor(), shape = MaterialTheme.shapes.large)
                .padding(horizontal = 16.dp)
                .height(dimensionResource(id = R.dimen.qsb_widget_height) + 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            LawnQsbUi(
                state = rememberAllAppsQsbState(
                    searchProvider = provider,
                    themed = themed,
                    shouldShowIcons = showIcons,
                    queryEmpty = true,
                    showMic = voiceIntent != null,
                    showLens = lensIntent != null,
                ),
                style = buildQsbStyle(
                    context = context,
                    themed = themed,
                    backgroundColor = ColorTokens.SearchboxHighlight.resolveColor(context),
                    backgroundAlpha = backgroundAlpha,
                    cornerRadius = cornerRadiusFactor,
                    // Use light color as strokeColor is a static color that doesn't use darkColor
                    strokeColor = strokeColor.colorPreferenceEntry.lightColor.invoke(context),
                    strokeWidth = strokeWidth,
                ),
                actions = QsbActions(onQsbClick = {}, onEndIconClick = {}),
                modifier = Modifier.height(48.dp),
            )
        }
    }
}
