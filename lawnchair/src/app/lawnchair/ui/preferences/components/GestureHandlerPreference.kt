package app.lawnchair.ui.preferences.components

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.RadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.config.GestureHandlerOption
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.AlertBottomSheetContent
import app.lawnchair.ui.util.LocalBottomSheetHandler
import kotlinx.coroutines.launch

val options = listOf(
    GestureHandlerOption.NoOp,
    GestureHandlerOption.Sleep,
    GestureHandlerOption.OpenNotifications,
    GestureHandlerOption.OpenAppDrawer,
    GestureHandlerOption.OpenAppSearch,
    GestureHandlerOption.OpenSearch,
    GestureHandlerOption.OpenApp,
)

@Composable
fun GestureHandlerPreference(
    adapter: PreferenceAdapter<GestureHandlerConfig>,
    label: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bottomSheetHandler = LocalBottomSheetHandler.current

    val currentConfig = adapter.state.value

    fun onSelect(option: GestureHandlerOption) {
        scope.launch {
            val config = option.buildConfig(context as Activity) ?: return@launch
            adapter.onChange(config)
        }
    }

    PreferenceTemplate(
        title = { Text(text = label) },
        description = { Text(text = currentConfig.getLabel(context)) },
        modifier = Modifier.clickable {
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
                        itemsIndexed(options) { index, option ->
                            if (index > 0) {
                                PreferenceDivider(startIndent = 40.dp)
                            }
                            val selected = currentConfig::class.java == option.configClass
                            PreferenceTemplate(
                                title = { Text(option.getLabel(context)) },
                                modifier = Modifier.clickable {
                                    bottomSheetHandler.hide()
                                    onSelect(option)
                                },
                                startWidget = {
                                    RadioButton(
                                        selected = selected,
                                        onClick = null
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    )
}
