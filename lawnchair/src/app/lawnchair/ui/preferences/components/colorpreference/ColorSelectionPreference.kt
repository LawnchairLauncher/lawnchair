package app.lawnchair.ui.preferences.components.colorpreference

import android.content.Context
import android.graphics.Color
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.colorpreference.pickers.CustomColorPicker
import app.lawnchair.ui.preferences.components.colorpreference.pickers.PresetsList
import app.lawnchair.ui.preferences.components.colorpreference.pickers.SwatchGrid
import app.lawnchair.ui.preferences.components.layout.BottomSpacer
import app.lawnchair.ui.preferences.components.layout.Chip
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R
import com.patrykmichalik.opto.domain.Preference
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ColorSelection(
    label: String,
    preference: Preference<ColorOption, String, *>,
    modifier: Modifier = Modifier,
    dynamicEntries: List<ColorPreferenceEntry<ColorOption>> = dynamicColors,
    staticEntries: List<ColorPreferenceEntry<ColorOption>> = staticColors,
) {
    val adapter = preference.getAdapter()
    val appliedColor = adapter.state.value
    val context = LocalContext.current
    val selectedColor = remember { mutableIntStateOf(appliedColor.forCustomPicker(context)) }
    val selectedColorApplied = remember {
        derivedStateOf {
            appliedColor is ColorOption.CustomColor && appliedColor.color == selectedColor.intValue
        }
    }
    val defaultTabIndex = when {
        dynamicEntries.any { it.value == appliedColor } -> 0
        staticEntries.any { it.value == appliedColor } -> 0
        else -> 1
    }

    val onPresetClick = { option: ColorOption ->
        selectedColor.intValue = option.forCustomPicker(context)
        adapter.onChange(newValue = option)
    }

    val pagerState = rememberPagerState(
        initialPage = defaultTabIndex,
        pageCount = { 2 },
    )
    PreferenceLayout(
        label = label,
        modifier = modifier,
        bottomBar = {
            if (pagerState.currentPage == 0) {
                BottomSpacer()
                return@PreferenceLayout
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                Button(
                    enabled = !selectedColorApplied.value,
                    onClick = { adapter.onChange(newValue = ColorOption.CustomColor(selectedColor.intValue)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 16.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(id = R.string.action_apply))
                }
                BottomSpacer()
            }
        },
    ) {
        val scope = rememberCoroutineScope()
        val scrollToPage =
            { page: Int -> scope.launch { pagerState.animateScrollToPage(page) } }

        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Chip(
                    label = stringResource(id = R.string.presets),
                    onClick = { scrollToPage(0) },
                    currentOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                    page = 0,
                )
                Chip(
                    label = stringResource(id = R.string.custom),
                    onClick = { scrollToPage(1) },
                    currentOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                    page = 1,
                )
            }
            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.animateContentSize(),
            ) { page ->
                when (page) {
                    0 -> {
                        Column {
                            PresetsList(
                                dynamicEntries = dynamicEntries,
                                onPresetClick = onPresetClick,
                                isPresetSelected = { it == appliedColor },
                            )
                            SwatchGrid(
                                modifier = Modifier.padding(top = 12.dp),
                                contentModifier = Modifier.padding(
                                    start = 16.dp,
                                    top = 20.dp,
                                    end = 16.dp,
                                    bottom = 16.dp,
                                ),
                                entries = staticEntries,
                                onSwatchClick = onPresetClick,
                                isSwatchSelected = { it == appliedColor },
                            )
                        }
                    }

                    1 -> {
                        CustomColorPicker(
                            selectedColor = selectedColor.intValue,
                            onSelect = { selectedColor.intValue = it },
                        )
                    }
                }
            }
        }
    }
}

private fun ColorOption.forCustomPicker(context: Context): Int {
    val color = colorPreferenceEntry.lightColor(context)
    if (color == 0) {
        return Color.BLACK
    }
    return color
}
