package app.lawnchair.ui.preferences.components.colorpreference

import android.content.Context
import android.graphics.Color
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import app.lawnchair.preferences.getAdapter
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.BottomSpacer
import app.lawnchair.ui.preferences.components.Chip
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.colorpreference.pickers.CustomColorPicker
import app.lawnchair.ui.preferences.components.colorpreference.pickers.PresetsList
import app.lawnchair.ui.preferences.components.colorpreference.pickers.SwatchGrid
import app.lawnchair.ui.preferences.preferenceGraph
import com.android.launcher3.R
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.patrykmichalik.opto.domain.Preference
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.colorSelectionGraph(route: String) {
    preferenceGraph(route, {}) { subRoute ->
        composable(
            route = subRoute("{prefKey}"),
            arguments = listOf(
                navArgument("prefKey") { type = NavType.StringType },
            ),
        ) { backStackEntry ->

            val args = backStackEntry.arguments!!
            val prefKey = args.getString("prefKey")!!
            val modelList = ColorPreferenceModelList.INSTANCE.get(LocalContext.current)
            val model = modelList[prefKey]
            ColorSelection(
                label = stringResource(id = model.labelRes),
                preference = model.prefObject,
                dynamicEntries = model.dynamicEntries,
            )
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun ColorSelection(
    label: String,
    preference: Preference<ColorOption, String, *>,
    dynamicEntries: List<ColorPreferenceEntry<ColorOption>> = dynamicColors,
    staticEntries: List<ColorPreferenceEntry<ColorOption>> = staticColors,
) {
    val adapter = preference.getAdapter()
    val appliedColor = adapter.state.value
    val context = LocalContext.current
    val selectedColor = remember { mutableStateOf(appliedColor.forCustomPicker(context)) }
    val selectedColorApplied = derivedStateOf {
        appliedColor is ColorOption.CustomColor && appliedColor.color == selectedColor.value
    }
    val defaultTabIndex = when {
        dynamicEntries.any { it.value == appliedColor } -> 0
        staticEntries.any { it.value == appliedColor } -> 1
        else -> 2
    }

    val onPresetClick = { option: ColorOption ->
        selectedColor.value = option.forCustomPicker(context)
        adapter.onChange(newValue = option)
    }

    val pagerState = rememberPagerState(defaultTabIndex)
    PreferenceLayout(
        label = label,
        bottomBar = {
            if (pagerState.currentPage == 0) {
                BottomSpacer()
                return@PreferenceLayout
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Button(
                    enabled = !selectedColorApplied.value,
                    onClick = { adapter.onChange(newValue = ColorOption.CustomColor(selectedColor.value)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 16.dp),
                ) {
                    Text(text = stringResource(id = R.string.apply_grid))
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
                    currentOffset = pagerState.currentPage + pagerState.currentPageOffset,
                    page = 0,
                )
                Chip(
                    label = stringResource(id = R.string.custom),
                    onClick = { scrollToPage(1) },
                    currentOffset = pagerState.currentPage + pagerState.currentPageOffset,
                    page = 1,
                )
            }
            HorizontalPager(
                count = 2,
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
                            selectedColor = selectedColor.value,
                            onSelect = { selectedColor.value = it },
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
