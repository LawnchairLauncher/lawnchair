package app.mica.gestures.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.mica.gestures.config.GestureHandlerConfig
import app.mica.gestures.config.GestureHandlerOption
import app.mica.gestures.config.buildConfigFrom
import app.mica.gestures.config.filterGestureHandlerOptions
import app.mica.preferences.getAdapter
import app.mica.preferences2.preferenceManager2
import app.mica.ui.preferences.components.controls.ClickablePreference
import app.mica.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.mica.ui.preferences.components.layout.preferenceGroupItems
import com.android.launcher3.R
import kotlinx.coroutines.launch

@Composable
fun CreateActionsScreen(
    modifier: Modifier = Modifier,
    onSelect: (GestureHandlerConfig) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs2 = preferenceManager2()
    val newOptions =
        filterGestureHandlerOptions(deckLayoutEnabled = prefs2.deckLayout.getAdapter().state.value)

    fun onClick(option: GestureHandlerOption) {
        scope.launch {
            val config = option.buildConfigFrom(context) ?: return@launch
            onSelect(config)
        }
    }

    PreferenceLayoutLazyColumn(
        label = stringResource(id = R.string.mica_actions),
        modifier = modifier,
    ) {
        preferenceGroupItems(items = newOptions, isFirstChild = true) { _, it ->
            ClickablePreference(
                label = it.getLabel(context),
                onClick = { onClick(it) },
            )
        }
    }
}
