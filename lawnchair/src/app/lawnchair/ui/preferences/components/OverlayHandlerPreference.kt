package app.lawnchair.ui.preferences.components

import android.R as AndroidR
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.components.layout.PreferenceDivider
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.util.LocalBottomSheetHandler
import app.lawnchair.views.overlay.FullScreenOverlayMode
import kotlinx.coroutines.launch

val overlayOptions = listOf(
    FullScreenOverlayMode.NONE,
    FullScreenOverlayMode.SUCK_IN,
    FullScreenOverlayMode.FADE_IN,
)

@Composable
fun OverlayHandlerPreference(
    adapter: PreferenceAdapter<FullScreenOverlayMode>,
    label: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val bottomSheetHandler = LocalBottomSheetHandler.current

    val currentConfig = adapter.state.value

    fun onSelect(option: FullScreenOverlayMode) {
        scope.launch {
            adapter.onChange(option)
        }
    }

    PreferenceTemplate(
        title = { Text(text = label) },
        description = { Text(text = stringResource(currentConfig.labelRes)) },
        modifier = modifier.clickable {
            bottomSheetHandler.show {
                ModalBottomSheetContent(
                    title = { Text(label) },
                    buttons = {
                        OutlinedButton(onClick = { bottomSheetHandler.hide() }) {
                            Text(text = stringResource(id = AndroidR.string.cancel))
                        }
                    },
                ) {
                    LazyColumn {
                        itemsIndexed(overlayOptions) { index, option ->
                            if (index > 0) {
                                PreferenceDivider(startIndent = 40.dp)
                            }
                            val selected = currentConfig == option
                            PreferenceTemplate(
                                title = { Text(text = stringResource(option.labelRes)) },
                                modifier = Modifier.clickable {
                                    bottomSheetHandler.hide()
                                    onSelect(option)
                                },
                                startWidget = {
                                    RadioButton(
                                        selected = selected,
                                        onClick = null,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}
