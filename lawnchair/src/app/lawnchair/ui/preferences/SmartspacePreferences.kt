package app.lawnchair.ui.preferences

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavGraphBuilder
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
import app.lawnchair.ui.preferences.components.DividerColumn
import app.lawnchair.ui.preferences.components.ExpandAndShrink
import app.lawnchair.ui.preferences.components.ListPreference
import app.lawnchair.ui.preferences.components.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.SwitchPreference
import app.lawnchair.ui.theme.isSelectedThemeDark
import com.android.launcher3.R
import com.kieronquinn.app.smartspacer.sdk.SmartspacerConstants

fun NavGraphBuilder.smartspaceGraph(route: String) {
    preferenceGraph(route, { SmartspacePreferences(fromWidget = false) })
}

fun NavGraphBuilder.smartspaceWidgetGraph(route: String) {
    preferenceGraph(route, { SmartspacePreferences(fromWidget = true) })
}

@Composable
fun SmartspacePreferences(fromWidget: Boolean) {
    val preferenceManager2 = preferenceManager2()
    val smartspaceProvider = SmartspaceProvider.INSTANCE.get(LocalContext.current)
    val smartspaceAdapter = preferenceManager2.enableSmartspace.getAdapter()
    val smartspaceModeAdapter = preferenceManager2.smartspaceMode.getAdapter()
    val smartspaceModeSelectionAdapter = preferenceManager2.smartspaceModeSelection.getAdapter()
    val selectedMode = smartspaceModeAdapter.state.value
    val modeIsLawnchair = selectedMode == LawnchairSmartspace

    PreferenceLayout(label = stringResource(id = R.string.smartspace_widget)) {
        if (!fromWidget) {
            PreferenceGroup(description = stringResource(id = R.string.smartspace_widget_toggle_description).takeIf { modeIsLawnchair }) {
                SwitchPreference(
                    adapter = smartspaceAdapter,
                    label = stringResource(id = R.string.smartspace_widget_toggle_label)
                )
                ExpandAndShrink(visible = smartspaceAdapter.state.value && smartspaceModeSelectionAdapter.state.value) {
                    SmartspaceProviderPreference(
                        adapter = smartspaceModeAdapter,
                        endWidget = when (selectedMode) {
                            Smartspacer -> {
                                { SmartspacerSettings() }
                            }
                            else -> null
                        }
                    )
                }
            }
        }
        Crossfade(
            targetState = (smartspaceAdapter.state.value || fromWidget) && modeIsLawnchair,
            label = "",
        ) { targetState ->
            if (targetState) {
                Column {
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
        }
    }
}

@Composable
fun SmartspaceProviderPreference(
    adapter: PreferenceAdapter<SmartspaceMode>,
    endWidget: (@Composable () -> Unit)? = null,
) {

    val context = LocalContext.current

    val entries = remember {
        SmartspaceMode.values().map { mode ->
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
        label = stringResource(id = R.string.smartspace_mode_label),
        endWidget = endWidget
    )
}

@Composable
fun SmartspacePreview() {
    val themeRes = if (isSelectedThemeDark()) R.style.AppTheme_Dark else R.style.AppTheme_DarkText
    val context = LocalContext.current
    val themedContext = remember(themeRes) { ContextThemeWrapper(context, themeRes) }

    PreferenceGroup(heading = stringResource(id = R.string.preview_label)) {
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
fun SmartspaceDateAndTimePreferences() {
    PreferenceGroup(
        heading = stringResource(id = R.string.smartspace_date_and_time),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        val preferenceManager2 = preferenceManager2()

        val calendarSelectionAdapter = preferenceManager2.enableSmartspaceCalendarSelection.getAdapter()
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
fun SmartspaceTimeFormatPreference() {

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
    )
}

@Composable
fun SmartspaceCalendarPreference() {

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
    )
}

@Composable
fun SmartspacerSettings() {
    val context = LocalContext.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clickable {
                val intent = context.packageManager.getLaunchIntentForPackage(
                    SmartspacerConstants.SMARTSPACER_PACKAGE_NAME
                )
                context.startActivity(intent)
            }
            .fillMaxHeight()
            .padding(horizontal = 16.dp)
            .size(32.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_setting),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
