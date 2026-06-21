package app.lawnchair.ui.preferences.components.search

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.hotseat.DisabledHotseat
import app.lawnchair.hotseat.HotseatMode
import app.lawnchair.hotseat.LawnchairHotseat
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.qsb.LawnQsbLayout
import app.lawnchair.qsb.LawnQsbUi
import app.lawnchair.qsb.QsbActions
import app.lawnchair.qsb.buildQsbStyle
import app.lawnchair.qsb.getHotseatBackgroundColor
import app.lawnchair.qsb.providers.Google
import app.lawnchair.qsb.providers.PixelSearch
import app.lawnchair.qsb.providers.QsbSearchProvider
import app.lawnchair.qsb.rememberHotseatQsbState
import app.lawnchair.theme.UiColorMode
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.tokens.ColorTokens
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.navigation.DockSearchProvider
import app.lawnchair.ui.theme.isSelectedThemeDark
import com.android.launcher3.R

@Composable
fun DockSearchPreference(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()

    val isHotseatEnabled = prefs2.isHotseatEnabled.getAdapter()
    val hotseatModeAdapter = prefs2.hotseatMode.getAdapter()
    val themeQsbAdapter = prefs2.themedHotseatQsb.getAdapter()
    val qsbCornerAdapter = prefs.hotseatQsbCornerRadius.getAdapter()
    val qsbAlphaAdapter = prefs.hotseatQsbAlpha.getAdapter()
    val qsbHotseatStrokeWidth = prefs.hotseatQsbStrokeWidth.getAdapter()
    val strokeColorStyleAdapter = prefs2.strokeColorStyle.getAdapter()
    val hotseatQsbProviderAdapter by prefs2.hotseatQsbProvider.getAdapter()

    Crossfade(isHotseatEnabled.state.value, label = "transition", modifier = modifier) { hotseatEnabled ->
        val isLawnchairHotseat = hotseatModeAdapter.state.value == LawnchairHotseat
        if (hotseatEnabled) {
            Column {
                PreferenceGroup {
                    Item {
                        HotseatModePreference(
                            adapter = hotseatModeAdapter,
                        )
                    }
                }
                ExpandAndShrink(visible = hotseatModeAdapter.state.value != DisabledHotseat) {
                    Column {
                        DockSearchBarPreview(
                            provider = hotseatQsbProviderAdapter,
                            themed = themeQsbAdapter.state.value,
                            cornerRadiusFactor = qsbCornerAdapter.state.value,
                            transparency = qsbAlphaAdapter.state.value,
                            strokeWidth = qsbHotseatStrokeWidth.state.value,
                            strokeColor = strokeColorStyleAdapter.state.value,
                        )
                    }
                }
                ExpandAndShrink(visible = isLawnchairHotseat) {
                    Column {
                        PreferenceGroup(
                            heading = stringResource(R.string.search_bar_settings),
                        ) {
                            Item {
                                NavigationActionPreference(
                                    label = stringResource(R.string.search_provider),
                                    destination = DockSearchProvider,
                                    subtitle = stringResource(
                                        id = QsbSearchProvider.values()
                                            .first { it == hotseatQsbProviderAdapter }
                                            .name,
                                    ),
                                )
                            }
                        }
                        PreferenceGroup(
                            heading = stringResource(R.string.style),
                        ) {
                            Item {
                                SwitchPreference(
                                    adapter = themeQsbAdapter,
                                    label = stringResource(id = R.string.apply_accent_color_label),
                                )
                            }
                            Item {
                                SliderPreference(
                                    label = stringResource(id = R.string.corner_radius_label),
                                    adapter = qsbCornerAdapter,
                                    step = 0.05F,
                                    valueRange = 0F..1F,
                                    showAsPercentage = true,
                                )
                            }
                            Item {
                                SliderPreference(
                                    label = stringResource(id = R.string.qsb_hotseat_background_transparency),
                                    adapter = qsbAlphaAdapter,
                                    step = 5,
                                    valueRange = 0..100,
                                    showUnit = "%",
                                )
                            }
                            Item {
                                SliderPreference(
                                    label = stringResource(id = R.string.qsb_hotseat_stroke_width),
                                    adapter = qsbHotseatStrokeWidth,
                                    step = 1f,
                                    valueRange = 0f..10f,
                                    showUnit = "vw",
                                )
                            }
                            if (qsbHotseatStrokeWidth.state.value > 0f) {
                                Item { ColorPreference(preference = prefs2.strokeColorStyle) }
                            }
                        }
                    }
                }
            }
        } else {
            PreferenceTemplate(
                modifier = Modifier
                    .clickable {
                        isHotseatEnabled.onChange(true)
                    }
                    .padding(horizontal = 16.dp),
                title = {},
                description = {
                    Text(
                        text = stringResource(id = R.string.enable_dock_to_access_qsb_settings),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                startWidget = {
                    Icon(
                        imageVector = Icons.Rounded.TipsAndUpdates,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

@Composable
private fun DockSearchBarPreview(
    provider: QsbSearchProvider,
    themed: Boolean,
    cornerRadiusFactor: Float,
    transparency: Int,
    strokeWidth: Float,
    strokeColor: ColorOption,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val searchProvider = provider
    val supportsLens = searchProvider == Google || searchProvider == PixelSearch
    val voiceIntent = remember(searchProvider, context) {
        LawnQsbLayout.getVoiceIntent(searchProvider, context)
    }
    val lensIntent = remember(supportsLens, context) {
        if (supportsLens) LawnQsbLayout.getLensIntent(context) else null
    }

    PreferenceGroup(
        heading = stringResource(id = R.string.preview_label),
    ) {
        Item {
            Box(
                modifier = modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(dimensionResource(id = R.dimen.qsb_widget_height) + 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                LawnQsbUi(
                    state = rememberHotseatQsbState(
                        searchProvider,
                        themed,
                        showMic = voiceIntent != null,
                        showLens = lensIntent != null,
                    ),
                    style = buildQsbStyle(
                        context = context,
                        themed = themed,
                        backgroundColor = getHotseatBackgroundColor(
                            context,
                            themed,
                            getThemedQsbBackgroundColor(),
                        ),
                        cornerRadius = cornerRadiusFactor,
                        backgroundAlpha = transparency,
                        strokeWidth = strokeWidth,
                        // Use light color as strokeColor is a static color that doesn't use darkColor
                        strokeColor = strokeColor.colorPreferenceEntry.lightColor.invoke(context),
                    ),
                    actions = QsbActions(
                        onQsbClick = {},
                        onEndIconClick = {},
                    ),
                    modifier = Modifier.height(48.dp),
                )
            }
        }
    }
}

@Composable
private fun HotseatModePreference(
    adapter: PreferenceAdapter<HotseatMode>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val entries = remember {
        HotseatMode.values().map { mode ->
            ListPreferenceEntry(
                value = mode,
                label = { stringResource(id = mode.nameResourceId) },
                enabled = mode.isAvailable(context = context),
            )
        }
    }

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(id = R.string.hotseat_mode_label),
        modifier = modifier,
    )
}

@Composable
private fun getThemedQsbBackgroundColor(): Int {
    return ColorTokens.ColorBackground.resolveColor(
        LocalContext.current,
        if (isSelectedThemeDark) UiColorMode.Dark else UiColorMode.Light,
    )
}
