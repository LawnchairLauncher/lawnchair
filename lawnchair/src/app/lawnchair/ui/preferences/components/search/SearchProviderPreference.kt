package app.lawnchair.ui.preferences.components.search

import android.Manifest
import android.provider.SearchRecentSuggestions
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.Keep
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.theme.dividerColor
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
        SearchProviderId.CALCULATOR -> R.string.all_apps_search_result_calculator
        SearchProviderId.CONTACTS -> R.string.search_pref_result_people_title
        SearchProviderId.FILES -> R.string.search_pref_result_files_title
        SearchProviderId.HISTORY -> R.string.search_pref_result_history_title
        SearchProviderId.SETTINGS -> R.string.search_pref_result_settings_title
        SearchProviderId.WEB -> R.string.search_pref_result_web_title
    }
}

@Composable
fun SearchProviderPreferenceItem(
    adapter: PreferenceAdapter<Boolean>,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String? = null,
) {
    PreferenceTemplate(
        modifier = modifier.clickable(onClick = onClick),
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        title = { Text(text = label) },
        description = { description?.let { Text(text = it) } },
        endWidget = {
            Spacer(
                modifier = Modifier
                    .height(32.dp)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(dividerColor()),
            )
            Switch(
                modifier = Modifier
                    .padding(all = 16.dp)
                    .height(24.dp),
                checked = enabled && adapter.state.value,
                onCheckedChange = adapter::onChange,
                enabled = enabled,
                thumbContent = {
                    if (enabled && adapter.state.value) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                },
            )
        },
        applyPaddings = false,
    )
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
            SearchProviderId.CONTACTS -> ContactsSearchProvider()
            SearchProviderId.FILES -> FileSearchProvider()
            else -> GenericSearchProviderPreference(provider)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ContactsSearchProvider(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()

    val context = LocalContext.current
    val contactsPermissionState = rememberPermissionState(Manifest.permission.READ_CONTACTS)

    var showPermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(contactsPermissionState.status) {
        if (!contactsPermissionState.status.isGranted) {
            showPermissionDialog = true
        }
    }

    val adapter = prefs.searchResultPeople.getAdapter()
    MainSwitchPreference(
        checked = adapter.state.value && contactsPermissionState.status.isGranted,
        onCheckedChange = adapter::onChange,
        label = stringResource(R.string.search_pref_result_people_title),
        modifier = modifier,
        enabled = contactsPermissionState.status.isGranted,
    ) {
        PreferenceGroup {
            Item {
                SliderPreference(
                    label = stringResource(R.string.max_people_result_count_title),
                    adapter = prefs2.maxPeopleResultCount.getAdapter(),
                    valueRange = 2..10,
                    step = 1,
                )
            }
        }
    }

    if (showPermissionDialog) {
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

        PermissionDialog(
            title = stringResource(R.string.warn_contact_permission_title),
            text = stringResource(id = R.string.warn_contact_permission_content, stringResource(id = R.string.derived_app_name)),
            isPermanentlyDenied = contactsPermissionState.status.shouldShowRationale,
            onConfirm = { contactsPermissionState.launchPermissionRequest() },
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
            Item {
                SliderPreference(
                    label = stringResource(
                        when (provider) {
                            SearchProviderId.APPS -> R.string.max_apps_result_count_title
                            SearchProviderId.HISTORY -> R.string.max_recent_result_count_title
                            SearchProviderId.SETTINGS -> R.string.max_settings_entry_result_count_title
                            SearchProviderId.WEB -> R.string.max_suggestion_result_count_title
                            else -> return@Item
                        },
                    ),
                    adapter = prefs2.let {
                        when (provider) {
                            SearchProviderId.APPS -> it.maxAppSearchResultCount
                            SearchProviderId.HISTORY -> it.maxRecentResultCount
                            SearchProviderId.SETTINGS -> it.maxSettingsEntryResultCount
                            SearchProviderId.WEB -> it.maxWebSuggestionResultCount
                            else -> return@Item
                        }.getAdapter()
                    },
                    valueRange = 2..10,
                    step = 1,
                )
            }

            when (provider) {
                SearchProviderId.APPS -> {
                    Item {
                        SwitchPreference(
                            adapter = prefs2.enableFuzzySearch.getAdapter(),
                            label = stringResource(id = R.string.fuzzy_search_title),
                            description = stringResource(id = R.string.fuzzy_search_desc),
                        )
                    }
                }

                SearchProviderId.WEB -> {
                    Item {
                        SliderPreference(
                            label = stringResource(id = R.string.max_web_suggestion_delay),
                            adapter = prefs2.maxWebSuggestionDelay.getAdapter(),
                            step = 500,
                            valueRange = 500..5000,
                            showUnit = "ms",
                        )
                    }
                    Item {
                        WebSearchProvider(
                            adapter = prefs2.webSuggestionProvider.getAdapter(),
                            nameAdapter = prefs2.webSuggestionProviderName.getAdapter(),
                            urlAdapter = prefs2.webSuggestionProviderUrl.getAdapter(),
                            suggestionsUrlAdapter = prefs2.webSuggestionProviderSuggestionsUrl.getAdapter(),
                        )
                    }
                }

                SearchProviderId.HISTORY -> {
                    Item {
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
                }

                else -> {}
            }
        }
    }
}
