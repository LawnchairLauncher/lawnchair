package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.qsb.providers.GlobalSearchApp
import app.lawnchair.qsb.providers.GlobalSearchAppInfo
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
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch

@Composable
fun SearchProviderPreferences(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bottomSheetHandler = LocalBottomSheetHandler.current
    val scope = rememberCoroutineScope()
    val preferences = preferenceManager2()
    val adapter = preferences.hotseatQsbProvider.getAdapter()
    val globalSearchPackageAdapter = preferences.hotseatQsbGlobalSearchPackage.getAdapter()
    val forceWebsiteAdapter = preferences.hotseatQsbForceWebsite.getAdapter()
    val knownProviderPackages = remember {
        QsbSearchProvider.values()
            .asSequence()
            .filterNot { it == GlobalSearchApp }
            .map { it.packageName }
            .filter(String::isNotBlank)
            .toSet()
    }

    PreferenceLayout(
        label = stringResource(R.string.search_provider),
        modifier = modifier,
    ) {
        PreferenceGroup(
            itemSpacing = 0.dp,
        ) {
            QsbSearchProvider.values().forEach { qsbSearchProvider ->
                val isGlobalSearchApp = qsbSearchProvider == GlobalSearchApp
                val appInstalled = qsbSearchProvider.isDownloaded(context)
                val selected = adapter.state.value == qsbSearchProvider
                val hasAppAndWebsite = qsbSearchProvider.type == QsbSearchProviderType.APP_AND_WEBSITE
                val showDownloadButton = qsbSearchProvider.type == QsbSearchProviderType.APP && !appInstalled
                Column {
                    val title = stringResource(id = qsbSearchProvider.name)
                    val globalSearchPackage = globalSearchPackageAdapter.state.value
                    val globalSearchAppDescription = if (isGlobalSearchApp) {
                        remember(context, globalSearchPackage) {
                            GlobalSearchApp.getApplicationLabel(context, globalSearchPackage)
                        } ?: globalSearchPackage.takeIf(String::isNotBlank)
                            ?: stringResource(R.string.search_provider_choose_app)
                    } else {
                        null
                    }
                    ListItem(
                        title = title,
                        showDownloadButton = showDownloadButton,
                        enabled = qsbSearchProvider.type != QsbSearchProviderType.APP || appInstalled,
                        selected = selected,
                        onClick = if (isGlobalSearchApp) {
                            {
                                val apps = GlobalSearchApp.queryInstalledApps(
                                    context = context,
                                    excludedPackages = knownProviderPackages,
                                )
                                bottomSheetHandler.show {
                                    GlobalSearchAppPicker(
                                        apps = apps,
                                        selectedPackage = globalSearchPackageAdapter.state.value,
                                        onSelect = { packageName ->
                                            bottomSheetHandler.hide()
                                            scope.launch {
                                                preferences.hotseatQsbGlobalSearchPackage.set(packageName)
                                                preferences.hotseatQsbProvider.set(GlobalSearchApp)
                                            }
                                        },
                                        onDismiss = { bottomSheetHandler.hide() },
                                    )
                                }
                            }
                        } else {
                            { adapter.onChange(newValue = qsbSearchProvider) }
                        },
                        onDownloadClick = { qsbSearchProvider.launchOnAppMarket(context = context) },
                        onSponsorDisclaimerClick = {
                            bottomSheetHandler.show {
                                SponsorDisclaimer(title) {
                                    bottomSheetHandler.hide()
                                }
                            }
                        }.takeIf { qsbSearchProvider.sponsored },
                        description = if (globalSearchAppDescription != null) {
                            globalSearchAppDescription
                        } else if (showDownloadButton) {
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
                    Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GlobalSearchAppPicker(
    apps: List<GlobalSearchAppInfo>,
    selectedPackage: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheetContent(
        title = { Text(stringResource(R.string.search_provider_select_app)) },
        buttons = {
            OutlinedButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        modifier = modifier,
    ) {
        if (apps.isEmpty()) {
            Text(
                text = stringResource(R.string.search_provider_no_compatible_apps),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            LazyColumn {
                itemsIndexed(
                    items = apps,
                    key = { _, app -> app.packageName },
                ) { index, app ->
                    if (index > 0) {
                        PreferenceDivider(startIndent = 56.dp)
                    }
                    PreferenceTemplate(
                        title = { Text(app.label) },
                        description = { Text(app.packageName) },
                        startWidget = {
                            Image(
                                painter = rememberDrawablePainter(app.icon),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .size(32.dp),
                            )
                        },
                        endWidget = {
                            RadioButton(
                                selected = selectedPackage == app.packageName,
                                onClick = null,
                                modifier = Modifier.padding(end = 16.dp),
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        onClick = { onSelect(app.packageName) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
            enabled = enabled,
            description = if (description != null) {
                { Text(text = description) }
            } else {
                null
            },
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
            onClick = if (enabled) onClick else null,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
            onClick = if (appEnabled) onAppClick else null,
        )
        PreferenceTemplate(
            title = { Text(text = stringResource(id = R.string.website_label)) },
            startWidget = {
                RadioButton(
                    selected = !appSelected,
                    onClick = null,
                    modifier = Modifier.padding(start = 56.dp),
                )
            },
            onClick = onWebsiteClick,
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
