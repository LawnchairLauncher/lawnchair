package app.lawnchair.ui.preferences.components.colorpreference

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.RadioButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.AlertBottomSheetContent
import app.lawnchair.ui.preferences.components.Chip
import app.lawnchair.ui.preferences.components.PreferenceTemplate
import app.lawnchair.ui.util.bottomSheetHandler
import com.android.launcher3.R
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterialApi::class, ExperimentalPagerApi::class)
fun ColorPreference2(
    value: ColorOption,
    label: String,
    dynamicEntries: List<ColorPreferenceEntry<ColorOption>>,
    staticEntries: List<ColorPreferenceEntry<ColorOption>>,
    onValueChange: (ColorOption) -> Unit,
) {
    val selectedEntry = dynamicEntries.firstOrNull { it.value == value } ?: staticEntries.firstOrNull { it.value == value }
    val defaultTabIndex = if (dynamicEntries.any { it.value == value }) 0 else 1
    val description = selectedEntry?.label?.invoke()
    val bottomSheetHandler = bottomSheetHandler
    var bottomSheetShown by remember { mutableStateOf(false) }

    PreferenceTemplate(
        title = { Text(text = label) },
        endWidget = { ColorDot(color = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable { bottomSheetShown = true },
        description = {
            if (description != null) {
                Text(text = description)
            }
        },
    )

    if (bottomSheetShown) {
        bottomSheetHandler.onDismiss { bottomSheetShown = false }
        bottomSheetHandler.show {
            val pagerState = rememberPagerState(defaultTabIndex)
            val scope = rememberCoroutineScope()
            val scrollToPage = { page: Int -> scope.launch { pagerState.animateScrollToPage(page) } }
            AlertBottomSheetContent(
                title = { Text(text = label) },
                buttons = {
                    Button(onClick = { bottomSheetHandler.hide() }) {
                        Text(text = stringResource(id = R.string.done))
                    }
                }
            ) {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Chip(
                            label = stringResource(id = R.string.dynamic),
                            onClick = { scrollToPage(0) },
                            currentOffset = pagerState.currentPage + pagerState.currentPageOffset,
                            page = 0,
                        )
                        Chip(
                            label = stringResource(id = R.string.presets),
                            onClick = { scrollToPage(1) },
                            currentOffset = pagerState.currentPage + pagerState.currentPageOffset,
                            page = 1,
                        )
                    }
                    HorizontalPager(
                        count = 2,
                        state = pagerState,
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.pagerHeight(
                            dynamicCount = dynamicEntries.size,
                            staticCount = staticEntries.size,
                        ),
                    ) { page ->
                        when (page) {
                            0 -> {
                                PresetsList(
                                    value = value,
                                    onValueChange = onValueChange,
                                    dynamicEntries = dynamicEntries,
                                )
                            }
                            1 -> {
                                SwatchGrid(
                                    entries = staticEntries,
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        top = 20.dp,
                                        end = 16.dp,
                                        bottom = 16.dp,
                                    ),
                                    onSwatchClick = { onValueChange(it) },
                                    isSwatchSelected = { it == value },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetsList(
    value: ColorOption,
    onValueChange: (ColorOption) -> Unit,
    dynamicEntries: List<ColorPreferenceEntry<ColorOption>>,
) {
    Box(
        modifier = Modifier.fillMaxHeight(),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(modifier = Modifier.padding(top = 16.dp)) {
            dynamicEntries.mapIndexed { index, entry ->
                key(entry) {
                    PreferenceTemplate(
                        title = { Text(text = entry.label()) },
                        verticalPadding = 12.dp,
                        modifier = Modifier.clickable { onValueChange(entry.value) },
                        showDivider = index > 0,
                        dividerIndent = 40.dp,
                        startWidget = {
                            RadioButton(
                                selected = entry.value == value,
                                onClick = null,
                            )
                            ColorDot(
                                entry = entry,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    )
                }
            }
        }
    }
}
