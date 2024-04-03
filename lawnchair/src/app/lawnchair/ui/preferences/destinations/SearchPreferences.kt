package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.not
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.HiddenAppsInSearchPreference
import app.lawnchair.ui.preferences.components.SearchSuggestionPreference
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.preferenceGraph
import app.lawnchair.util.checkAndRequestFilesPermission
import app.lawnchair.util.contactPermissionGranted
import app.lawnchair.util.filesAndStorageGranted
import app.lawnchair.util.requestContactPermissionGranted
import com.android.launcher3.R

fun NavGraphBuilder.searchGraph(route: String) {
    preferenceGraph(route, { SearchPreferences() })
}

@Composable
fun SearchPreferences() {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val context = LocalContext.current

    val showDrawerSearchBar = !prefs2.hideAppDrawerSearchBar.getAdapter()
    val hiddenApps = prefs2.hiddenApps.getAdapter().state.value

    PreferenceLayout(label = stringResource(id = R.string.drawer_search_label)) {
        MainSwitchPreference(adapter = showDrawerSearchBar, label = stringResource(id = R.string.show_app_search_bar)) {
            PreferenceGroup(heading = stringResource(R.string.general_label)) {
                ExpandAndShrink(visible = hiddenApps.isNotEmpty()) {
                    HiddenAppsInSearchPreference()
                }
                SwitchPreference(
                    adapter = prefs2.autoShowKeyboardInDrawer.getAdapter(),
                    label = stringResource(id = R.string.pref_search_auto_show_keyboard),
                )
            }

            val isDeviceSearch = prefs2.performWideSearch.getAdapter().state.value

            PreferenceGroup(heading = stringResource(id = R.string.show_search_result_types)) {
                SearchSuggestionPreference(
                    adapter = prefs.searchResultApps.getAdapter(),
                    maxCountAdapter = prefs2.maxSearchResultCount.getAdapter(),
                    maxCountRange = 3..15,
                    label = stringResource(R.string.search_pref_result_apps_and_shortcuts_title),
                    maxCountLabel = stringResource(R.string.max_apps_result_count_title),
                    preventSwitchChange = true,
                ) {
                    SwitchPreference(
                        adapter = prefs2.enableFuzzySearch.getAdapter(),
                        label = stringResource(id = R.string.fuzzy_search_title),
                        description = stringResource(id = R.string.fuzzy_search_desc),
                    )
                }
                SearchSuggestionPreference(
                    adapter = prefs.searchResultStartPageSuggestion.getAdapter(),
                    maxCountAdapter = prefs2.maxSuggestionResultCount.getAdapter(),
                    maxCountRange = 3..10,
                    label = stringResource(id = R.string.search_pref_result_web_title),
                    maxCountLabel = stringResource(id = R.string.max_suggestion_result_count_title),
                    description = stringResource(id = R.string.search_pref_result_web_description),
                ) {
                    SliderPreference(
                        label = stringResource(id = R.string.max_web_suggestion_delay),
                        adapter = prefs2.maxWebSuggestionDelay.getAdapter(),
                        step = 100,
                        valueRange = 200..5000,
                        showUnit = "ms",
                    )
                }

                SearchSuggestionPreference(
                    adapter = prefs.searchResultSettingsEntry.getAdapter(),
                    maxCountAdapter = prefs2.maxSettingsEntryResultCount.getAdapter(),
                    maxCountRange = 2..10,
                    label = stringResource(id = R.string.search_pref_result_settings_title),
                    maxCountLabel = stringResource(id = R.string.max_settings_entry_result_count_title),
                )

                if (isDeviceSearch) {
                    SearchSuggestionPreference(
                        adapter = prefs.searchResultPeople.getAdapter(),
                        maxCountAdapter = prefs2.maxPeopleResultCount.getAdapter(),
                        maxCountRange = 3..15,
                        label = stringResource(id = R.string.search_pref_result_people_title),
                        maxCountLabel = stringResource(id = R.string.max_people_result_count_title),
                        description = stringResource(id = R.string.search_pref_result_contacts_description),
                        isPermissionGranted = contactPermissionGranted(context),
                        onPermissionRequest = { requestContactPermissionGranted(context, prefs) },
                        requestPermissionDescription = stringResource(id = R.string.warn_contact_permission_content),
                    )
                    SearchSuggestionPreference(
                        adapter = prefs.searchResultFiles.getAdapter(),
                        maxCountAdapter = prefs2.maxFileResultCount.getAdapter(),
                        maxCountRange = 3..10,
                        label = stringResource(id = R.string.search_pref_result_files_title),
                        maxCountLabel = stringResource(id = R.string.max_file_result_count_title),
                        description = stringResource(id = R.string.search_pref_result_files_description),
                        isPermissionGranted = filesAndStorageGranted(context),
                        onPermissionRequest = { checkAndRequestFilesPermission(context, prefs) },
                        requestPermissionDescription = stringResource(id = R.string.warn_files_permission_content),
                    )
                    SearchSuggestionPreference(
                        adapter = prefs.searchResultSettingsEntry.getAdapter(),
                        maxCountAdapter = prefs2.maxSettingsEntryResultCount.getAdapter(),
                        maxCountRange = 2..10,
                        label = stringResource(id = R.string.search_pref_result_settings_title),
                        maxCountLabel = stringResource(id = R.string.max_settings_entry_result_count_title),
                    )
                }
                SearchSuggestionPreference(
                    adapter = prefs.searchResulRecentSuggestion.getAdapter(),
                    maxCountAdapter = prefs2.maxRecentResultCount.getAdapter(),
                    maxCountRange = 1..10,
                    label = stringResource(id = R.string.search_pref_result_history_title),
                    maxCountLabel = stringResource(id = R.string.max_recent_result_count_title),
                )
            }

//            if (deviceSearchEnabled) {
//                ExpandAndShrink(visible = showDrawerSearchBar.state.value) {
//                    PreferenceGroup(heading = stringResource(id = R.string.show_search_result_types)) {
//                        SwitchPreference(
//                            adapter = prefs.searchResultShortcuts.getAdapter(),
//                            label = stringResource(id = R.string.search_pref_result_shortcuts_title),
//                        )
//                        SwitchPreference(
//                            adapter = prefs.searchResultPeople.getAdapter(),
//                            label = stringResource(id = R.string.search_pref_result_people_title),
//                        )
//                        SwitchPreference(
//                            adapter = prefs.searchResultPixelTips.getAdapter(),
//                            label = stringResource(id = R.string.search_pref_result_tips_title),
//                        )
//                    }
//                }
//            }
        }
    }
}
