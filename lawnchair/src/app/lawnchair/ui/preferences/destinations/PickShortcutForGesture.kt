package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.gestures.AppShortcutEntry
import app.lawnchair.gestures.AppShortcuts
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.AppItemPlaceholder
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.util.kotlinxJson
import com.android.launcher3.R
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lists the app shortcuts of all apps this launcher may start (for example a pinned chat or a
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

    val shortcuts by produceState<List<AppShortcutEntry>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { AppShortcuts.load(context) }
    }

    fun onSelect(entry: AppShortcutEntry) {
        val configString = kotlinxJson.encodeToString(entry.toGestureConfig())
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
