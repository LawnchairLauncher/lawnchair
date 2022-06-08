package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.RadioButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.qsb.providers.QsbSearchProvider
import app.lawnchair.qsb.providers.QsbSearchProviderType
import app.lawnchair.ui.AlertBottomSheetContent
import app.lawnchair.ui.util.addIf
import app.lawnchair.ui.util.bottomSheetHandler
import com.android.launcher3.R

@Composable
fun QsbProviderPreference() {
    val adapter = preferenceManager2().hotseatQsbProvider.getAdapter()
    val context = LocalContext.current
    val entries = remember {
        QsbSearchProvider.values().map {
            // Enabled is true if provider type is not app or it is app & the app is installed
            val enabled = it.type != QsbSearchProviderType.APP || it.isDownloaded(context)
            ListPreferenceEntry(it, enabled) { stringResource(id = it.name) }
        }
    }

    QsbProviderListPreference(
        entries = entries,
        value = adapter.state.value,
        onValueChange = adapter::onChange,
        label = stringResource(R.string.search_provider),
    )
}

@Composable
fun QsbProviderListPreference(
    entries: List<ListPreferenceEntry<QsbSearchProvider>>,
    value: QsbSearchProvider,
    onValueChange: (QsbSearchProvider) -> Unit,
    label: String,
    enabled: Boolean = true,
    description: String? = null,
) {
    val context = LocalContext.current

    val bottomSheetHandler = bottomSheetHandler
    val currentDescription = description ?: entries
        .firstOrNull { it.value == value }
        ?.label?.invoke()

    val paddingVertical = 16.dp
    val paddingHorizontal = 16.dp

    PreferenceTemplate(
        title = { Text(text = label) },
        description = { currentDescription?.let { Text(text = it) } },
        enabled = enabled,
        modifier = Modifier.clickable(enabled) {
            bottomSheetHandler.show {
                AlertBottomSheetContent(
                    title = { Text(label) },
                    buttons = {
                        OutlinedButton(onClick = { bottomSheetHandler.hide() }) {
                            Text(text = stringResource(id = android.R.string.cancel))
                        }
                    }
                ) {
                    LazyColumn {
                        itemsIndexed(entries) { index, item ->
                            if (index > 0) {
                                PreferenceDivider(startIndent = 40.dp)
                            }
                            PreferenceTemplate(
                                enabled = item.enabled,
                                title = { Text(item.label()) },
                                description = {
                                    if (item.value.type == QsbSearchProviderType.APP) {
                                        Text(stringResource(id = R.string.qsb_search_provider_app_required))
                                    }
                                },
                                applyPaddings = false,
                                modifier = Modifier
                                    .clickable(item.enabled) {
                                        onValueChange(item.value)
                                        bottomSheetHandler.hide()
                                    },
                                startWidget = {
                                    RadioButton(
                                        modifier = Modifier.padding(
                                            horizontal = paddingHorizontal,
                                            vertical = paddingVertical
                                        ),
                                        selected = item.value == value,
                                        onClick = null,
                                        enabled = item.enabled,
                                    )
                                },
                                endWidget = {
                                    if (item.value.type.downloadable) {
                                        val downloaded = item.value.isDownloaded(context)
                                        Icon(
                                            modifier = Modifier
                                                .addIf(!downloaded) {
                                                    clickable {
                                                        item.value.launchOnAppMarket(context = context)
                                                        bottomSheetHandler.hide()
                                                    }
                                                }
                                                .padding(
                                                    horizontal = paddingHorizontal,
                                                    vertical = paddingVertical
                                                ),
                                            painter = painterResource(id = if (downloaded) R.drawable.ic_tick else R.drawable.ic_download),
                                            contentDescription = null,
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}