package app.lawnchair.ui.preferences.components

import android.R as AndroidR
import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.config.GestureHandlerOption
import app.lawnchair.gestures.type.GestureType
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.components.layout.PreferenceDivider
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.util.LocalBottomSheetHandler
import com.android.launcher3.util.ComponentKey
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.launch

val options = listOf(
    GestureHandlerOption.NoOp,
    GestureHandlerOption.Sleep,
    GestureHandlerOption.Recents,
    GestureHandlerOption.OpenNotifications,
    GestureHandlerOption.OpenAppDrawer,
    GestureHandlerOption.OpenAppSearch,
    GestureHandlerOption.OpenSearch,
    GestureHandlerOption.OpenApp,
    GestureHandlerOption.OpenAssistant,
)

@Composable
fun GestureHandlerPreference(
    adapter: PreferenceAdapter<GestureHandlerConfig>,
    label: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bottomSheetHandler = LocalBottomSheetHandler.current
    val pref2 = preferenceManager2()

    val currentConfig = adapter.state.value

    fun onSelect(option: GestureHandlerOption) {
        scope.launch {
            val config = option.buildConfig(context as Activity) ?: return@launch
            adapter.onChange(config)
        }
    }

    val newOptions = options.filterNot { option ->
        option in listOf(
            GestureHandlerOption.OpenAppDrawer,
            GestureHandlerOption.OpenAppSearch,
        ) &&
            pref2.deckLayout.firstBlocking()
    }

    PreferenceTemplate(
        title = { Text(text = label) },
        description = { Text(text = currentConfig.getLabel(context)) },
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
                        itemsIndexed(newOptions) { index, option ->
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

@Composable
fun AppGesturePreference(
    cmp: ComponentKey,
    gestureType: GestureType,
    label: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = preferenceManager2()

    var isExpanded by remember { mutableStateOf(false) }

    val currentConfig by produceState<GestureHandlerConfig>(initialValue = GestureHandlerConfig.NoOp) {
        prefs.getGestureForApp(cmp, gestureType).collect { value = it }
    }

    fun onSelect(option: GestureHandlerOption) {
        scope.launch {
            val config = option.buildConfig(context as Activity) ?: return@launch
            prefs.setGestureForApp(cmp, gestureType, config)
            isExpanded = false
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        PreferenceTemplate(
            title = { Text(text = label) },
            description = { Text(text = currentConfig.getLabel(context)) },
            modifier = Modifier
                .clickable { isExpanded = !isExpanded }
                .fillMaxWidth(),
        )

        AnimatedVisibility(visible = isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 300.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(options) { index, option ->
                        if (index > 0) {
                            PreferenceDivider(startIndent = 40.dp)
                        }
                        val selected = currentConfig::class.java == option.configClass
                        PreferenceTemplate(
                            title = { Text(option.getLabel(context)) },
                            modifier = Modifier.clickable {
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
    }
}
