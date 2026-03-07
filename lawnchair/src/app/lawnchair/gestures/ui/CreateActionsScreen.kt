package app.lawnchair.gestures.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.config.GestureHandlerOption
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.gestureHandlerOptions
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import com.android.launcher3.R
import com.patrykmichalik.opto.core.firstBlocking
import kotlin.collections.contains
import kotlinx.coroutines.launch

@Composable
fun CreateActionsScreen(
    modifier: Modifier = Modifier,
    onSelect: (GestureHandlerConfig) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs2 = preferenceManager2()
    val newOptions = gestureHandlerOptions.filterNot { option ->
        option in listOf(
            GestureHandlerOption.OpenAppDrawer,
            GestureHandlerOption.OpenAppSearch,
        ) &&
            prefs2.deckLayout.firstBlocking()
    }

    fun onClick(option: GestureHandlerOption) {
        scope.launch {
            val config = option.buildConfig(context as Activity) ?: return@launch
            onSelect(config)
        }
    }

    PreferenceLayoutLazyColumn(
        label = stringResource(id = R.string.lawnchair_actions),
        modifier = modifier,
    ) {
        preferenceGroupItems(items = newOptions, isFirstChild = true) { index, it ->
            ClickablePreference(
                label = it.getLabel(context),
                onClick = { onClick(it) },
            )
        }
    }
}
