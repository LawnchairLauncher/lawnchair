package app.lawnchair.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.qsb.providers.QsbSearchProvider
import app.lawnchair.qsb.providers.QsbSearchProviderType
import app.lawnchair.ui.preferences.components.ClickableIcon
import app.lawnchair.ui.preferences.components.DividerColumn
import app.lawnchair.ui.preferences.components.ExpandAndShrink
import app.lawnchair.ui.preferences.components.PreferenceDivider
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.PreferenceTemplate
import com.android.launcher3.R

fun NavGraphBuilder.searchProviderGraph(route: String) {
    preferenceGraph(route, { SearchProviderPreferences() })
}

@Composable
fun SearchProviderPreferences() {
    val context = LocalContext.current
    val adapter = preferenceManager2().hotseatQsbProvider.getAdapter()
    val forceWebsiteAdapter = preferenceManager2().hotseatQsbForceWebsite.getAdapter()
    PreferenceLayout(label = stringResource(R.string.search_provider)) {
        PreferenceGroup {
            QsbSearchProvider.values().forEach { qsbSearchProvider ->
                val appInstalled = qsbSearchProvider.isDownloaded(context)
                val selected = adapter.state.value == qsbSearchProvider
                val hasAppAndWebsite = qsbSearchProvider.type == QsbSearchProviderType.APP_AND_WEBSITE
                val showDownloadButton = qsbSearchProvider.type == QsbSearchProviderType.APP && !appInstalled
                Column {
                    ListItem(
                        title = stringResource(id = qsbSearchProvider.name),
                        showDownloadButton = showDownloadButton,
                        enabled = qsbSearchProvider.type != QsbSearchProviderType.APP || appInstalled,
                        selected = selected,
                        onClick = { adapter.onChange(newValue = qsbSearchProvider) },
                        onDownloadClick = { qsbSearchProvider.launchOnAppMarket(context = context) },
                        description = if (showDownloadButton) {
                            stringResource(id = R.string.qsb_search_provider_app_required)
                        } else null,
                    )
                    ExpandAndShrink(visible = selected && hasAppAndWebsite) {
                        Options(
                            appEnabled = appInstalled,
                            appSelected = !forceWebsiteAdapter.state.value && appInstalled,
                            onAppClick = { forceWebsiteAdapter.onChange(newValue = false) },
                            onAppDownloadClick = { qsbSearchProvider.launchOnAppMarket(context = context) },
                            onWebsiteClick = { forceWebsiteAdapter.onChange(newValue = true) },
                            showAppDownloadButton = !appInstalled,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListItem(
    title: String,
    description: String?,
    showDownloadButton: Boolean,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    Column {
        PreferenceTemplate(
            title = { Text(text = title) },
            verticalPadding = if (showDownloadButton) 12.dp else 16.dp,
            horizontalPadding = 0.dp,
            enabled = enabled,
            modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
            description = { if (description != null) Text(text = description) },
            startWidget = {
                RadioButton(
                    selected = selected,
                    onClick = null,
                    enabled = enabled,
                    modifier = Modifier.padding(start = 16.dp)
                )
            },
            endWidget = {
                if (showDownloadButton) {
                    ClickableIcon(
                        painter = painterResource(id = R.drawable.ic_download),
                        onClick = onDownloadClick,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            },
        )
    }
}

@Composable
private fun Options(
    appEnabled: Boolean,
    appSelected: Boolean,
    showAppDownloadButton: Boolean,
    onAppClick: () -> Unit,
    onAppDownloadClick: () -> Unit,
    onWebsiteClick: () -> Unit,
) {
    PreferenceDivider(startIndent = 40.dp)
    DividerColumn(startIndent = 40.dp) {
        PreferenceTemplate(
            title = { Text(stringResource(id = R.string.app_label)) },
            enabled = appEnabled,
            verticalPadding = if (!appEnabled) 4.dp else 16.dp,
            horizontalPadding = 0.dp,
            modifier = Modifier.clickable(
                enabled = appEnabled,
                onClick = onAppClick,
            ),
            startWidget = {
                RadioButton(
                    selected = appSelected,
                    onClick = null,
                    enabled = appEnabled,
                    modifier = Modifier.padding(start = 56.dp),
                )
            },
            endWidget = {
                if (showAppDownloadButton) {
                    ClickableIcon(
                        painter = painterResource(R.drawable.ic_download),
                        onClick = onAppDownloadClick,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            },
        )
        PreferenceTemplate(
            title = { Text(text = stringResource(id = R.string.website_label)) },
            modifier = Modifier.clickable(onClick = onWebsiteClick),
            horizontalPadding = 0.dp,
            startWidget = {
                RadioButton(
                    selected = !appSelected,
                    onClick = null,
                    modifier = Modifier.padding(start = 56.dp),
                )
            },
        )
    }
}
