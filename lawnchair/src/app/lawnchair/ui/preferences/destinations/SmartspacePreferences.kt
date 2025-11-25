package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.smartspace.SmartspaceViewContainer
import app.lawnchair.smartspace.model.LawnchairSmartspace
import app.lawnchair.smartspace.model.SmartspaceCalendar
import app.lawnchair.smartspace.model.SmartspaceMode
import app.lawnchair.smartspace.model.SmartspaceTimeFormat
import app.lawnchair.smartspace.model.Smartspacer
import app.lawnchair.smartspace.provider.SmartspaceProvider
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.DividerColumn
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.theme.isSelectedThemeDark
import com.android.launcher3.R
import com.kieronquinn.app.smartspacer.sdk.SmartspacerConstants

@Composable
fun SmartspacePreferences(
    fromWidget: Boolean,
    modifier: Modifier = Modifier,
) {
    val preferenceManager2 = preferenceManager2()
    val smartspaceProvider = SmartspaceProvider.INSTANCE.get(LocalContext.current)
    val smartspaceAdapter = preferenceManager2.enableSmartspace.getAdapter()
    val smartspaceModeAdapter = preferenceManager2.smartspaceMode.getAdapter()
    val selectedMode = smartspaceModeAdapter.state.value
    val modeIsLawnchair = selectedMode == LawnchairSmartspace

    PreferenceLayout(
        label = stringResource(id = R.string.smartspace_widget),
        backArrowVisible = !LocalIsExpandedScreen.current && !fromWidget,
        modifier = modifier,
    ) {
        if (fromWidget) {
            LawnchairSmartspaceSettings(smartspaceProvider)
        } else {
            MainSwitchPreference(
                adapter = smartspaceAdapter,
                label = stringResource(R.string.smartspace_widget_toggle_label),
                description = stringResource(id = R.string.smartspace_widget_toggle_description).takeIf { modeIsLawnchair },
            ) {
                PreferenceGroup {
                    SmartspaceProviderPreference(
                        adapter = smartspaceModeAdapter,
                    )
                }

                Crossfade(
                    targetState = selectedMode,
                    label = "Smartspace setting transision",
                ) { targetState ->
                    when (targetState) {
                        LawnchairSmartspace -> {
                            LawnchairSmartspaceSettings(smartspaceProvider)
                        }

                        Smartspacer -> {
                            SmartspacerSettings()
                        }

                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun LawnchairSmartspaceSettings(
    smartspaceProvider: SmartspaceProvider,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        SmartspacePreview()
        PreferenceGroup(
            heading = stringResource(id = R.string.what_to_show),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            smartspaceProvider.dataSources
                .asSequence()
                .filter { it.isAvailable }
                .forEach {
                    key(it.providerName) {
                        SwitchPreference(
                            adapter = it.enabledPref.getAdapter(),
                            label = stringResource(id = it.providerName),
                        )
                    }
                }
        }
        SmartspaceDateAndTimePreferences()
    }
}

@Composable
fun SmartspaceProviderPreference(
    adapter: PreferenceAdapter<SmartspaceMode>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val entries = remember {
        SmartspaceMode.values().map { mode ->
            ListPreferenceEntry(
                value = mode,
                label = { stringResource(id = mode.nameResourceId) },
                enabled = mode.isAvailable(context = context),
            )
        }.toList()
    }

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(id = R.string.smartspace_mode_label),
        modifier = modifier,
    )
}

@Composable
fun SmartspacePreview(
    modifier: Modifier = Modifier,
) {
    val themeRes = if (isSelectedThemeDark) R.style.AppTheme_Dark else R.style.AppTheme_DarkText
    val context = LocalContext.current
    val themedContext = remember(themeRes) { ContextThemeWrapper(context, themeRes) }

    PreferenceGroup(
        heading = stringResource(id = R.string.preview_label),
        modifier = modifier,
    ) {
        CompositionLocalProvider(LocalContext provides themedContext) {
            AndroidView(
                factory = {
                    val view = SmartspaceViewContainer(it, previewMode = true)
                    val height = it.resources
                        .getDimensionPixelSize(R.dimen.enhanced_smartspace_height)
                    view.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, height)
                    view
                },
                modifier = Modifier.padding(
                    start = 8.dp,
                    top = 8.dp,
                    bottom = 16.dp,
                ),
            )
        }
        LaunchedEffect(key1 = null) {
            SmartspaceProvider.INSTANCE.get(context).startSetup(context as Activity)
        }
    }
}

@Composable
fun SmartspaceDateAndTimePreferences(
    modifier: Modifier = Modifier,
) {
    PreferenceGroup(
        heading = stringResource(id = R.string.smartspace_date_and_time),
        modifier = modifier.padding(top = 8.dp),
    ) {
        val preferenceManager2 = preferenceManager2()

        val calendarSelectionAdapter =
            preferenceManager2.enableSmartspaceCalendarSelection.getAdapter()
        val calendarAdapter = preferenceManager2.smartspaceCalendar.getAdapter()
        val showDateAdapter = preferenceManager2.smartspaceShowDate.getAdapter()
        val showTimeAdapter = preferenceManager2.smartspaceShowTime.getAdapter()

        val calendarHasMinimumContent = !showDateAdapter.state.value || !showTimeAdapter.state.value

        val calendar = if (calendarSelectionAdapter.state.value) {
            calendarAdapter.state.value
        } else {
            preferenceManager2.smartspaceCalendar.defaultValue
        }

        ExpandAndShrink(visible = calendar.formatCustomizationSupport) {
            DividerColumn {
                SwitchPreference(
                    adapter = showDateAdapter,
                    label = stringResource(id = R.string.smartspace_date),
                    enabled = if (showDateAdapter.state.value) !calendarHasMinimumContent else true,
                )
                val calendarSelectionEnabled =
                    preferenceManager2.enableSmartspaceCalendarSelection.getAdapter()
                ExpandAndShrink(visible = calendarSelectionEnabled.state.value && showDateAdapter.state.value) {
                    SmartspaceCalendarPreference()
                }
                SwitchPreference(
                    adapter = showTimeAdapter,
                    label = stringResource(id = R.string.smartspace_time),
                    enabled = if (showTimeAdapter.state.value) !calendarHasMinimumContent else true,
                )
                ExpandAndShrink(visible = showTimeAdapter.state.value) {
                    SmartspaceTimeFormatPreference()
                }
            }
        }
    }
}

@Composable
fun SmartspaceTimeFormatPreference(
    modifier: Modifier = Modifier,
) {
    val entries = remember {
        SmartspaceTimeFormat.values().map { format ->
            ListPreferenceEntry(format) { stringResource(id = format.nameResourceId) }
        }
    }

    val adapter = preferenceManager2().smartspaceTimeFormat.getAdapter()

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(id = R.string.smartspace_time_format),
        modifier = modifier,
    )
}

@Composable
fun SmartspaceCalendarPreference(
    modifier: Modifier = Modifier,
) {
    val entries = remember {
        SmartspaceCalendar.values().map { calendar ->
            ListPreferenceEntry(calendar) { stringResource(id = calendar.nameResourceId) }
        }
    }

    val adapter = preferenceManager2().smartspaceCalendar.getAdapter()

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(id = R.string.smartspace_calendar),
        modifier = modifier,
    )
}

@Composable
fun SmartspacerSettings(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()

    Column(modifier) {
        PreferenceGroup(
            heading = stringResource(id = R.string.smartspacer_settings),
        ) {
            SliderPreference(
                label = stringResource(R.string.maximum_number_of_targets),
                adapter = prefs2.smartspacerMaxCount.getAdapter(),
                valueRange = 5..15,
                step = 1,
            )
            ClickablePreference(label = stringResource(R.string.open_smartspacer_settings)) {
                val intent = context.packageManager.getLaunchIntentForPackage(
                    SmartspacerConstants.SMARTSPACER_PACKAGE_NAME,
                )
                context.startActivity(intent)
            }
        }
    }
}
