package app.lawnchair.ui.preferences.destinations

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.not
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.search.algorithms.LawnchairSearchAlgorithm
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.HiddenAppsInSearchPreference
import app.lawnchair.ui.preferences.components.SearchSuggestionPreference
import app.lawnchair.ui.preferences.components.WebSearchProvider
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.util.checkAndRequestFilesPermission
import app.lawnchair.util.filesAndStorageGranted
import com.android.launcher3.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState

@Composable
fun SearchPreferences() {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val context = LocalContext.current

    val showDrawerSearchBar = !prefs2.hideAppDrawerSearchBar.getAdapter()
    val hiddenApps = prefs2.hiddenApps.getAdapter().state.value

    PreferenceLayout(
        label = stringResource(id = R.string.drawer_search_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
    ) {
        MainSwitchPreference(
            adapter = showDrawerSearchBar,
            label = stringResource(id = R.string.show_app_search_bar),
        ) {
            PreferenceGroup(heading = stringResource(R.string.general_label)) {
                ExpandAndShrink(visible = hiddenApps.isNotEmpty()) {
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

            PreferenceGroup(heading = stringResource(id = R.string.show_search_result_types)) {
                val searchAlgorithm = preferenceManager2().searchAlgorithm.getAdapter().state.value
                if (searchAlgorithm != LawnchairSearchAlgorithm.ASI_SEARCH) {
                    val canDisable = searchAlgorithm != LawnchairSearchAlgorithm.APP_SEARCH
                    val adapter = prefs.searchResultApps.getAdapter()

                    SearchSuggestionPreference(
                        checked = if (canDisable) adapter.state.value else true,
                        onCheckedChange = if (canDisable) adapter::onChange else ({}),
                        enabled = if (canDisable) true else false,
                        onRequestPermission = {},
                        maxCountAdapter = prefs2.maxAppSearchResultCount.getAdapter(),
                        maxCountRange = 3..15,
                        label = stringResource(R.string.search_pref_result_apps_and_shortcuts_title),
                        maxCountLabel = stringResource(R.string.max_apps_result_count_title),
                    ) {
                        SwitchPreference(
                            adapter = prefs2.enableFuzzySearch.getAdapter(),
                            label = stringResource(id = R.string.fuzzy_search_title),
                            description = stringResource(id = R.string.fuzzy_search_desc),
                        )
                    }
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
                LawnchairSearchAlgorithm.ASI_SEARCH -> LawnchairSearchAlgorithm.isASISearchEnabled(context)
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
    val contactsPermissionState = rememberPermissionState(
        android.Manifest.permission.READ_CONTACTS,
    )
    val filesPermissionState =
        rememberPermissionState(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
        )

    val webSuggestionProvider = stringResource(prefs2.webSuggestionProvider.getAdapter().state.value.label)
    SearchSuggestionPreference(
        adapter = prefs.searchResultStartPageSuggestion.getAdapter(),
        maxCountAdapter = prefs2.maxWebSuggestionResultCount.getAdapter(),
        maxCountRange = 3..10,
        label = stringResource(id = R.string.search_pref_result_web_title),
        maxCountLabel = stringResource(id = R.string.max_suggestion_result_count_title),
        description = stringResource(id = R.string.search_pref_result_web_provider_description, webSuggestionProvider),
    ) {
        SliderPreference(
            label = stringResource(id = R.string.max_web_suggestion_delay),
            adapter = prefs2.maxWebSuggestionDelay.getAdapter(),
            step = 500,
            valueRange = 500..5000,
            showUnit = "ms",
        )
        WebSearchProvider(
            adapter = prefs2.webSuggestionProvider.getAdapter(),
        )
    }
    SearchSuggestionPreference(
        adapter = prefs.searchResultPeople.getAdapter(),
        maxCountAdapter = prefs2.maxPeopleResultCount.getAdapter(),
        maxCountRange = 3..15,
        label = stringResource(id = R.string.search_pref_result_people_title),
        maxCountLabel = stringResource(id = R.string.max_people_result_count_title),
        description = stringResource(id = R.string.search_pref_result_contacts_description),
        permissionState = contactsPermissionState,
        permissionRationale = stringResource(id = R.string.warn_contact_permission_content),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val lifecycleOwner = LocalLifecycleOwner.current
        val state by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
        var isGranted by remember { mutableStateOf(filesAndStorageGranted(context)) }
        // TODO refactor permission handling of all files access

        LaunchedEffect(state) {
            if (!filesAndStorageGranted(context)) {
                isGranted = false
                prefs.searchResultFiles.set(false)
            }

            if (state == Lifecycle.State.RESUMED) {
                isGranted = filesAndStorageGranted(context)
            }
        }

        SearchSuggestionPreference(
            adapter = prefs.searchResultFiles.getAdapter(),
            maxCountAdapter = prefs2.maxFileResultCount.getAdapter(),
            maxCountRange = 3..10,
            label = stringResource(id = R.string.search_pref_result_files_title),
            maxCountLabel = stringResource(id = R.string.max_file_result_count_title),
            description = stringResource(id = R.string.search_pref_result_files_description),
            isGranted = filesAndStorageGranted(context),
            onRequestPermission = {
                checkAndRequestFilesPermission(context, prefs)
            },
            permissionRationale = stringResource(id = R.string.warn_files_permission_content),
        )
    } else {
        SearchSuggestionPreference(
            adapter = prefs.searchResultFiles.getAdapter(),
            maxCountAdapter = prefs2.maxFileResultCount.getAdapter(),
            maxCountRange = 3..10,
            label = stringResource(id = R.string.search_pref_result_files_title),
            maxCountLabel = stringResource(id = R.string.max_file_result_count_title),
            description = stringResource(id = R.string.search_pref_result_files_description),
            permissionState = filesPermissionState,
            permissionRationale = stringResource(id = R.string.warn_files_permission_content),
        )
    }
    SearchSuggestionPreference(
        adapter = prefs.searchResultSettingsEntry.getAdapter(),
        maxCountAdapter = prefs2.maxSettingsEntryResultCount.getAdapter(),
        maxCountRange = 2..10,
        label = stringResource(id = R.string.search_pref_result_settings_title),
        maxCountLabel = stringResource(id = R.string.max_settings_entry_result_count_title),
    )
    SearchSuggestionPreference(
        adapter = prefs.searchResulRecentSuggestion.getAdapter(),
        maxCountAdapter = prefs2.maxRecentResultCount.getAdapter(),
        maxCountRange = 1..10,
        label = stringResource(id = R.string.search_pref_result_history_title),
        maxCountLabel = stringResource(id = R.string.max_recent_result_count_title),
    )
    SwitchPreference(
        adapter = prefs.searchResultCalculator.getAdapter(),
        label = stringResource(R.string.all_apps_search_result_calculator),
    )
}
