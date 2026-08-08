package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.asCoroutineDispatcher
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
    var selectedAppKey by remember { mutableStateOf<ComponentKey?>(null) }

    fun onSelect(config: GestureHandlerConfig) {
        if (activity == null) return

        val configString = kotlinxJson.encodeToString(config)
        activity.setResult(Activity.RESULT_OK, Intent().putExtra("config", configString))
        activity.finish()
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
                                onClick = {
                                    mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                                    selectedAppKey = if (selectedAppKey == app.key) null else app.key
                                },
                            )
                            ExpandAndShrink(visible = selectedAppKey == app.key) {
                                AppShortcutOptions(app = app, onSelect = ::onSelect)
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
    onSelect: (GestureHandlerConfig) -> Unit,
) {
    val context = LocalContext.current
    val shortcuts by produceState<List<ShortcutInfo>?>(initialValue = null, app.key) {
        value = withContext(MODEL_EXECUTOR.asCoroutineDispatcher()) {
            runCatching {
                getPublishedShortcuts(context, app.key)
            }.getOrElse { error ->
                Log.w(TAG, "Unable to load app shortcuts", error)
                emptyList()
            }
        }
    }

    PreferenceDivider(startIndent = NestedOptionIndent)
    DividerColumn(startIndent = NestedOptionIndent) {
        AppItem(
            app = app,
            widget = { Spacer(Modifier.requiredWidth(NestedOptionIndent)) },
            onClick = {
                onSelect(
                    GestureHandlerConfig.OpenApp(
                        appName = app.label,
                        target = OpenAppTarget.App(app.key),
                    ),
                )
            },
        )

        when (val publishedShortcuts = shortcuts) {
            null -> AppItemPlaceholder(
                widget = { Spacer(Modifier.requiredWidth(NestedOptionIndent)) },
            )

            else -> publishedShortcuts.forEach { shortcut ->
                AppItem(
                    label = shortcut.shortLabel.toString(),
                    icon = app.icon,
                    widget = { Spacer(Modifier.requiredWidth(NestedOptionIndent)) },
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
}

private const val TAG = "PickAppForGesture"
private val NestedOptionIndent = 40.dp

private fun getPublishedShortcuts(context: Context, componentKey: ComponentKey): List<ShortcutInfo> {
    val shortcuts = ShortcutRequest(context, componentKey.user)
        .withContainer(componentKey.componentName)
        .query(ShortcutRequest.PUBLISHED)
    return PopupPopulator.sortAndFilterShortcuts(shortcuts)
}
