package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.handlers.OpenAppTarget
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.AppItemPlaceholder
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.util.kotlinxJson
import app.lawnchair.util.toBitmap
import com.android.launcher3.R
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** LC: one app shortcut (dynamic, manifest or pinned) that a gesture can open. */
private data class ShortcutEntry(
    val label: String,
    val icon: Bitmap,
    val target: OpenAppTarget.Shortcut,
)

/**
 * Lists the app shortcuts this launcher may start (for example a pinned chat or a
 * "new message" action) so that one can be bound to a gesture.
 */
@Composable
fun PickShortcutForGesture(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as Activity
    val msdlPlayer = MSDLPlayerWrapper.INSTANCE.get(context)
    val state = rememberLazyListState()

    val shortcuts by produceState<List<ShortcutEntry>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { loadShortcuts(context) }
    }

    fun onSelect(entry: ShortcutEntry) {
        val config: GestureHandlerConfig = GestureHandlerConfig.OpenApp(
            appName = entry.label,
            target = entry.target,
        )
        val configString = kotlinxJson.encodeToString(config)
        activity.setResult(Activity.RESULT_OK, Intent().putExtra("config", configString))
        activity.finish()
    }

    PreferenceScaffold(
        label = stringResource(id = R.string.pick_shortcut_for_gesture),
        isExpandedScreen = LocalIsExpandedScreen.current,
        modifier = modifier,
    ) { padding ->
        val list = shortcuts
        when {
            list == null -> PreferenceLazyColumn(padding, enabled = false) {
                preferenceGroupItems(count = 20, isFirstChild = true) {
                    AppItemPlaceholder()
                }
            }

            list.isEmpty() -> PreferenceLazyColumn(padding) {
                preferenceGroupItems(count = 1, isFirstChild = true) {
                    PreferenceTemplate(
                        title = { Text(text = stringResource(id = R.string.pick_shortcut_for_gesture_empty)) },
                    )
                }
            }

            else -> PreferenceLazyColumn(padding, state = state) {
                preferenceGroupItems(items = list, isFirstChild = true) { _, entry ->
                    AppItem(
                        label = entry.label,
                        icon = entry.icon,
                        onClick = {
                            msdlPlayer.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                            onSelect(entry)
                        },
                    )
                }
            }
        }
    }
}

private fun loadShortcuts(context: Context): List<ShortcutEntry> {
    val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
    if (!launcherApps.hasShortcutHostPermission()) return emptyList()
    val pm = context.packageManager
    val density = context.resources.displayMetrics.densityDpi
    val query = ShortcutQuery().setQueryFlags(
        ShortcutQuery.FLAG_MATCH_DYNAMIC or
            ShortcutQuery.FLAG_MATCH_MANIFEST or
            ShortcutQuery.FLAG_MATCH_PINNED,
    )
    val result = mutableListOf<ShortcutEntry>()
    for (user in UserCache.INSTANCE.get(context).userProfiles) {
        val infos: List<ShortcutInfo> = try {
            launcherApps.getShortcuts(query, user).orEmpty()
        } catch (e: Exception) {
            Log.w("PickShortcutForGesture", "Failed to query shortcuts", e)
            emptyList()
        }
        for (info in infos) {
            if (!info.isEnabled) continue
            val appLabel = try {
                pm.getApplicationLabel(pm.getApplicationInfo(info.`package`, 0)).toString()
            } catch (_: Exception) {
                info.`package`
            }
            val shortcutLabel = (info.longLabel ?: info.shortLabel)?.toString().orEmpty()
            val icon = try {
                launcherApps.getShortcutIconDrawable(info, density)
            } catch (_: Exception) {
                null
            } ?: pm.defaultActivityIcon
            result += ShortcutEntry(
                label = if (shortcutLabel.isEmpty()) appLabel else "$appLabel: $shortcutLabel",
                icon = icon.toBitmap(),
                target = OpenAppTarget.Shortcut(
                    intent = Intent(Intent.ACTION_MAIN).setPackage(info.`package`),
                    user = info.userHandle,
                    packageName = info.`package`,
                    id = info.id,
                ),
            )
        }
    }
    return result.distinctBy { Triple(it.target.packageName, it.target.id, it.target.user) }
        .sortedBy { it.label.lowercase() }
}
