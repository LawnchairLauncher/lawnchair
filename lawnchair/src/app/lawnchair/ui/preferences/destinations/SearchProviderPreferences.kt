package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.qsb.providers.QsbSearchProvider
import app.lawnchair.qsb.providers.QsbSearchProviderType
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.components.layout.ClickableIcon
import app.lawnchair.ui.preferences.components.layout.DividerColumn
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceDivider
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.util.LocalBottomSheetHandler
import com.android.launcher3.R

@Composable
fun SearchProviderPreferences(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bottomSheetHandler = LocalBottomSheetHandler.current
    val adapter = preferenceManager2().hotseatQsbProvider.getAdapter()
    val forceWebsiteAdapter = preferenceManager2().hotseatQsbForceWebsite.getAdapter()

    PreferenceLayout(
        label = stringResource(R.string.search_provider),
        modifier = modifier,
    ) {
        PreferenceGroup {
            QsbSearchProvider.values().forEach { qsbSearchProvider ->
                val appInstalled = qsbSearchProvider.isDownloaded(context)
                val selected = adapter.state.value == qsbSearchProvider
                val hasAppAndWebsite = qsbSearchProvider.type == QsbSearchProviderType.APP_AND_WEBSITE
                val showDownloadButton = qsbSearchProvider.type == QsbSearchProviderType.APP && !appInstalled
                Column {
                    val title = stringResource(id = qsbSearchProvider.name)
                    ListItem(
                        title = title,
                        showDownloadButton = showDownloadButton,
                        enabled = qsbSearchProvider.type != QsbSearchProviderType.APP || appInstalled,
                        selected = selected,
                        onClick = { adapter.onChange(newValue = qsbSearchProvider) },
                        onDownloadClick = { qsbSearchProvider.launchOnAppMarket(context = context) },
                        onSponsorDisclaimerClick = {
                            bottomSheetHandler.show {
                                SponsorDisclaimer(title) {
                                    bottomSheetHandler.hide()
                                }
                            }
                        }.takeIf { qsbSearchProvider.sponsored },
                        description = if (showDownloadButton) {
                            stringResource(id = R.string.qsb_search_provider_app_required)
                        } else {
                            null
                        },
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
    onSponsorDisclaimerClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
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
                    modifier = Modifier.padding(start = 16.dp),
                )
            },
            endWidget = {
                Row {
                    if (onSponsorDisclaimerClick != null) {
                        ClickableIcon(
                            painter = painterResource(id = R.drawable.ic_about),
                            onClick = onSponsorDisclaimerClick,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    if (showDownloadButton) {
                        ClickableIcon(
                            painter = painterResource(id = R.drawable.ic_download),
                            onClick = onDownloadClick,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
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
    modifier: Modifier = Modifier,
) {
    PreferenceDivider(startIndent = 40.dp)
    DividerColumn(
        modifier = modifier,
        startIndent = 40.dp,
    ) {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SponsorDisclaimer(
    sponsor: String,
    modifier: Modifier = Modifier,
    onAcknowledge: () -> Unit,
) {
    ModalBottomSheetContent(
        buttons = {
            OutlinedButton(
                onClick = onAcknowledge,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        modifier = modifier,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            LocalTextStyle provides MaterialTheme.typography.bodyLarge,
        ) {
            Text(
                text = stringResource(id = R.string.search_provider_sponsored_description, sponsor),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp),
            )
        }
    }
}
