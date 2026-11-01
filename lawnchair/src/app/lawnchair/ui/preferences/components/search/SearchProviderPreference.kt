package app.lawnchair.ui.preferences.components.search

import android.Manifest
import android.provider.SearchRecentSuggestions
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.search.LawnchairRecentSuggestionProvider
import app.lawnchair.ui.preferences.components.PermissionDialog
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.util.openAppPermissionSettings
import com.android.launcher3.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.serialization.Serializable

@Serializable
@Keep
enum class SearchProviderId(val id: String) {
    APPS("apps"),
    CALENDAR("calendar"),
    CALCULATOR("calculator"),
    CONTACTS("contacts"),
    FILES("files"),
    HISTORY("history"),
    SETTINGS("settings"),
    WEB("web"),
}

fun getProviderName(provider: SearchProviderId): Int {
    return when (provider) {
        SearchProviderId.APPS -> R.string.search_pref_result_apps_and_shortcuts_title
        SearchProviderId.CALENDAR -> R.string.search_pref_result_calendar_title
        SearchProviderId.CALCULATOR -> R.string.all_apps_search_result_calculator
        SearchProviderId.CONTACTS -> R.string.search_pref_result_people_title
        SearchProviderId.FILES -> R.string.search_pref_result_files_title
        SearchProviderId.HISTORY -> R.string.search_pref_result_history_title
        SearchProviderId.SETTINGS -> R.string.search_pref_result_settings_title
        SearchProviderId.WEB -> R.string.search_pref_result_web_title
    }
}

@Composable
fun SearchProviderPreferenceScreen(
    provider: SearchProviderId,
    modifier: Modifier = Modifier,
) {
    PreferenceLayout(
        label = stringResource(getProviderName(provider)),
        modifier = modifier,
    ) {
        when (provider) {
            SearchProviderId.CALENDAR -> CalendarSearchProvider()
            SearchProviderId.CONTACTS -> ContactsSearchProvider()
            SearchProviderId.FILES -> FileSearchProvider()
            else -> GenericSearchProviderPreference(provider)
        }
    }
}

@Composable
fun CalendarSearchProvider(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()

    PermissionGatedSearchProviderPreference(
        permission = Manifest.permission.READ_CALENDAR,
        enabledAdapter = prefs.searchResultCalendar.getAdapter(),
        label = stringResource(R.string.search_pref_result_calendar_title),
        maxResultCountLabel = stringResource(R.string.max_calendar_result_count_title),
        maxResultCountAdapter = prefs2.maxCalendarResultCount.getAdapter(),
        permissionTitle = stringResource(R.string.warn_calendar_permission_title),
        permissionContent = stringResource(
            id = R.string.warn_calendar_permission_content,
            stringResource(id = R.string.derived_app_name),
        ),
        modifier = modifier,
    ) {
        SwitchPreference(
            adapter = prefs2.showCalendarCanceledEvents.getAdapter(),
            label = stringResource(R.string.calendar_search_show_canceled_title),
            description = stringResource(R.string.calendar_search_show_canceled_description),
        )
    }
}

@Composable
fun ContactsSearchProvider(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()

    PermissionGatedSearchProviderPreference(
        permission = Manifest.permission.READ_CONTACTS,
        enabledAdapter = prefs.searchResultPeople.getAdapter(),
        label = stringResource(R.string.search_pref_result_people_title),
        maxResultCountLabel = stringResource(R.string.max_people_result_count_title),
        maxResultCountAdapter = prefs2.maxPeopleResultCount.getAdapter(),
        permissionTitle = stringResource(R.string.warn_contact_permission_title),
        permissionContent = stringResource(
            id = R.string.warn_contact_permission_content,
            stringResource(id = R.string.derived_app_name),
        ),
        modifier = modifier,
    )
}

/**
 * Use when a search provider needs additional runtime [permission].
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionGatedSearchProviderPreference(
    permission: String,
    enabledAdapter: PreferenceAdapter<Boolean>,
    label: String,
    maxResultCountLabel: String,
    maxResultCountAdapter: PreferenceAdapter<Int>,
    permissionTitle: String,
    permissionContent: String,
    modifier: Modifier = Modifier,
    extraContent: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val permissionState = rememberPermissionState(permission)
    var showPermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(permissionState.status) {
        if (!permissionState.status.isGranted) {
            showPermissionDialog = true
        }
    }

    MainSwitchPreference(
        checked = enabledAdapter.state.value && permissionState.status.isGranted,
        onCheckedChange = enabledAdapter::onChange,
        label = label,
        modifier = modifier,
        enabled = permissionState.status.isGranted,
    ) {
        PreferenceGroup {
            SliderPreference(
                label = maxResultCountLabel,
                adapter = maxResultCountAdapter,
                valueRange = 2..10,
                step = 1,
            )
            extraContent()
        }
    }

    if (showPermissionDialog) {
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

        PermissionDialog(
            title = permissionTitle,
            text = permissionContent,
            isPermanentlyDenied = permissionState.status.shouldShowRationale,
            onConfirm = { permissionState.launchPermissionRequest() },
            onDismiss = {
                backDispatcher?.onBackPressed()
                showPermissionDialog = false
            },
            onGoToSettings = { context.openAppPermissionSettings() },
        )
    }
}

@Composable
fun GenericSearchProviderPreference(
    provider: SearchProviderId,
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()

    val adapter = prefs.let {
        when (provider) {
            SearchProviderId.APPS -> it.searchResultApps
            SearchProviderId.HISTORY -> it.searchResulRecentSuggestion
            SearchProviderId.SETTINGS -> it.searchResultSettingsEntry
            SearchProviderId.WEB -> it.searchResultStartPageSuggestion
            else -> return
        }
    }.getAdapter()

    MainSwitchPreference(
        adapter = adapter,
        label = stringResource(getProviderName(provider)),
        modifier = modifier,
    ) {
        PreferenceGroup {
            SliderPreference(
                label = stringResource(
                    when (provider) {
                        SearchProviderId.APPS -> R.string.max_apps_result_count_title
                        SearchProviderId.HISTORY -> R.string.max_recent_result_count_title
                        SearchProviderId.SETTINGS -> R.string.max_settings_entry_result_count_title
                        SearchProviderId.WEB -> R.string.max_suggestion_result_count_title
                        else -> return@PreferenceGroup
                    },
                ),
                adapter = prefs2.let {
                    when (provider) {
                        SearchProviderId.APPS -> it.maxAppSearchResultCount
                        SearchProviderId.HISTORY -> it.maxRecentResultCount
                        SearchProviderId.SETTINGS -> it.maxSettingsEntryResultCount
                        SearchProviderId.WEB -> it.maxWebSuggestionResultCount
                        else -> return@PreferenceGroup
                    }.getAdapter()
                },
                valueRange = 2..10,
                step = 1,
            )

            when (provider) {
                SearchProviderId.APPS -> {
                    SwitchPreference(
                        adapter = prefs2.enableFuzzySearch.getAdapter(),
                        label = stringResource(id = R.string.fuzzy_search_title),
                        description = stringResource(id = R.string.fuzzy_search_desc),
                    )
                }

                SearchProviderId.WEB -> {
                    SliderPreference(
                        label = stringResource(id = R.string.max_web_suggestion_delay),
                        adapter = prefs2.maxWebSuggestionDelay.getAdapter(),
                        step = 500,
                        valueRange = 500..5000,
                        showUnit = "ms",
                    )
                    WebSearchProvider(
                        adapter = prefs2.webSuggestionProvider.getAdapter(),
                        nameAdapter = prefs2.webSuggestionProviderName.getAdapter(),
                        urlAdapter = prefs2.webSuggestionProviderUrl.getAdapter(),
                        suggestionsUrlAdapter = prefs2.webSuggestionProviderSuggestionsUrl.getAdapter(),
                    )
                }

                SearchProviderId.HISTORY -> {
                    val context = LocalContext.current

                    val suggestionsRecent = SearchRecentSuggestions(
                        context,
                        LawnchairRecentSuggestionProvider.AUTHORITY,
                        LawnchairRecentSuggestionProvider.MODE,
                    )

                    ClickablePreference(
                        label = stringResource(id = R.string.clear_history),
                        onClick = {
                            suggestionsRecent.clearHistory()
                        },
                    )
                }

                else -> {}
            }
        }
    }
}
