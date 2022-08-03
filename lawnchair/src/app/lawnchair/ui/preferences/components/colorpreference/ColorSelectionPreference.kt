package app.lawnchair.ui.preferences.components.colorpreference

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.*
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
            val preferenceManager2 = preferenceManager2()
            val pref = when (prefKey) {
                preferenceManager2.accentColor.key.name -> preferenceManager2.accentColor
                preferenceManager2.notificationDotColor.key.name -> preferenceManager2.notificationDotColor
                else -> return@composable
            }
            val label = when (prefKey) {
                preferenceManager2.accentColor.key.name -> stringResource(id = R.string.accent_color)
                preferenceManager2.notificationDotColor.key.name -> stringResource(id = R.string.notification_dots_color)
                else -> return@composable
            }
            val dynamicEntries = when (prefKey) {
                preferenceManager2.notificationDotColor.key.name -> dynamicColorsForNotificationDot
                else -> dynamicColors
            }
            ColorSelection(
                label = label,
                preference = pref,
                dynamicEntries = dynamicEntries,
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
    val appliedColor by adapter
    val selectedColor = remember { mutableStateOf(appliedColor) }
    val defaultTabIndex = when {
        dynamicEntries.any { it.value == appliedColor } -> 0
        staticEntries.any { it.value == appliedColor } -> 1
        else -> 2
    }

    PreferenceLayout(
        label = label,
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Button(
                    enabled = selectedColor.value != appliedColor,
                    onClick = { adapter.onChange(newValue = selectedColor.value) },
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

        val pagerState = rememberPagerState(defaultTabIndex)
        val scope = rememberCoroutineScope()
        val scrollToPage =
            { page: Int -> scope.launch { pagerState.animateScrollToPage(page) } }

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
                Chip(
                    label = stringResource(id = R.string.custom),
                    onClick = { scrollToPage(2) },
                    currentOffset = pagerState.currentPage + pagerState.currentPageOffset,
                    page = 2,
                )
            }
            HorizontalPager(
                count = 3,
                state = pagerState,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.animateContentSize(),
            ) { page ->
                when (page) {
                    0 -> {
                        PresetsList(
                            dynamicEntries = dynamicEntries,
                            onPresetClick = { selectedColor.value = it },
                            isPresetSelected = { it == selectedColor.value },
                        )
                    }
                    1 -> {
                        SwatchGrid(
                            modifier = Modifier.padding(top = 12.dp),
                            contentModifier = Modifier.padding(
                                start = 16.dp,
                                top = 20.dp,
                                end = 16.dp,
                                bottom = 16.dp,
                            ),
                            entries = staticEntries,
                            onSwatchClick = { selectedColor.value = it },
                            isSwatchSelected = { it == selectedColor.value },
                        )
                    }
                    2 -> {
                        CustomColorPicker(
                            selectedColorOption = selectedColor.value,
                            onSelect = { selectedColor.value = it },
                        )
                    }
                }
            }

        }
    }
}
