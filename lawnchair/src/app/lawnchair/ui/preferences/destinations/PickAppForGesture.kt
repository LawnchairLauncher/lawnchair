package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.handlers.OpenAppTarget
import app.lawnchair.gestures.handlers.OpenShortcutTarget
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.AppItemPlaceholder
import app.lawnchair.ui.preferences.components.layout.DividerColumn
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceDivider
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.util.App
import app.lawnchair.util.appsState
import app.lawnchair.util.kotlinxJson
import com.android.launcher3.R
import com.android.launcher3.popup.PopupPopulator
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PickAppForGesture(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context)
    val apps by appsState()
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedAppKey by remember { mutableStateOf<ComponentKey?>(null) }
    var selectedShortcuts by remember { mutableStateOf<List<ShortcutInfo>?>(null) }
    var shortcutLoadingJob by remember { mutableStateOf<Job?>(null) }

    fun onSelect(config: GestureHandlerConfig) {
        if (activity == null) return

        val configString = kotlinxJson.encodeToString(config)
        activity.setResult(Activity.RESULT_OK, Intent().putExtra("config", configString))
        activity.finish()
    }

    fun selectApp(app: App) {
        shortcutLoadingJob?.cancel()
        if (selectedAppKey == app.key) {
            selectedAppKey = null
            selectedShortcuts = null
            return
        }

        mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
        selectedAppKey = app.key
        selectedShortcuts = null
        shortcutLoadingJob = scope.launch {
            val shortcuts = withContext(MODEL_EXECUTOR.asCoroutineDispatcher()) {
                runCatching {
                    getPublishedShortcuts(context, app.key)
                }.getOrElse { error ->
                    Log.w(TAG, "Unable to load app shortcuts", error)
                    emptyList()
                }
            }
            if (selectedAppKey != app.key) return@launch

            if (shortcuts.isEmpty()) {
                onSelect(
                    GestureHandlerConfig.OpenApp(
                        appName = app.label,
                        target = OpenAppTarget.App(app.key),
                    ),
                )
            } else {
                selectedShortcuts = shortcuts
            }
        }
    }

    PreferenceScaffold(
        label = stringResource(id = R.string.pick_app_for_gesture),
        isExpandedScreen = LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        Crossfade(targetState = apps.isNotEmpty(), label = "") { present ->
            if (present) {
                PreferenceLazyColumn(it, state = state) {
                    preferenceGroupItems(
                        items = apps,
                        isFirstChild = true,
                    ) { _, app ->
                        Column {
                            AppItem(
                                app = app,
                                widget = {
                                    RadioButton(
                                        selected = selectedAppKey == app.key,
                                        onClick = null,
                                        modifier = Modifier.padding(start = ParentRadioButtonIndent),
                                    )
                                },
                                onClick = { selectApp(app) },
                            )
                            ExpandAndShrink(visible = selectedShortcuts != null && selectedAppKey == app.key) {
                                AppShortcutOptions(
                                    app = app,
                                    shortcuts = selectedShortcuts.orEmpty(),
                                    onSelect = ::onSelect,
                                )
                            }
                        }
                    }
                }
            } else {
                PreferenceLazyColumn(it, enabled = false) {
                    preferenceGroupItems(
                        count = 20,
                        isFirstChild = true,
                    ) {
                        AppItemPlaceholder()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppShortcutOptions(
    app: App,
    shortcuts: List<ShortcutInfo>,
    onSelect: (GestureHandlerConfig) -> Unit,
) {
    PreferenceDivider(startIndent = NestedOptionIndent)
    DividerColumn(startIndent = NestedOptionIndent) {
        GestureOption(
            label = stringResource(R.string.gesture_handler_open_app_option),
            onClick = {
                onSelect(
                    GestureHandlerConfig.OpenApp(
                        appName = app.label,
                        target = OpenAppTarget.App(app.key),
                    ),
                )
            },
        )

        shortcuts.forEach { shortcut ->
            GestureOption(
                label = shortcut.shortLabel.toString(),
                onClick = {
                    onSelect(
                        GestureHandlerConfig.OpenShortcut(
                            shortcutName = shortcut.shortLabel.toString(),
                            target = OpenShortcutTarget(
                                app = app.key,
                                user = shortcut.userHandle,
                                packageName = shortcut.`package`,
                                id = shortcut.id,
                            ),
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun GestureOption(
    label: String,
    onClick: () -> Unit,
) {
    PreferenceTemplate(
        title = { Text(text = label) },
        startWidget = {
            RadioButton(
                selected = false,
                onClick = null,
                modifier = Modifier.padding(start = NestedOptionRadioButtonIndent),
            )
        },
        onClick = onClick,
    )
}

private const val TAG = "PickAppForGesture"
private val NestedOptionIndent = 40.dp
private val ParentRadioButtonIndent = 16.dp
private val NestedOptionRadioButtonIndent = 56.dp

private fun getPublishedShortcuts(context: Context, componentKey: ComponentKey): List<ShortcutInfo> {
    val shortcuts = ShortcutRequest(context, componentKey.user)
        .withContainer(componentKey.componentName)
        .query(ShortcutRequest.PUBLISHED)
    return PopupPopulator.sortAndFilterShortcuts(shortcuts)
}
