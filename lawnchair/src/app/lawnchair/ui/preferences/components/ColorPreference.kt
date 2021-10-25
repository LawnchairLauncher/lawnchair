package app.lawnchair.ui.preferences.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.AlertBottomSheetContent
import app.lawnchair.ui.theme.lightenColor
import com.android.launcher3.R
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterialApi::class)
fun ColorPreference(
    adapter: PreferenceAdapter<ColorOption>,
    label: String,
    dynamicEntries: List<ColorPreferenceEntry<ColorOption>>,
    staticEntries: List<ColorPreferenceEntry<ColorOption>>
) {
    var selectedColor by adapter
    val selectedEntry = dynamicEntries.firstOrNull { it.value == selectedColor }
        ?: staticEntries.firstOrNull { it.value == selectedColor }
    val defaultTabIndex = if (dynamicEntries.any { it.value == selectedColor }) 0 else 1
    var selectedTabIndex by remember { mutableStateOf(value = defaultTabIndex) }
    val bottomSheetState = rememberBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()
    val description = selectedEntry?.label?.invoke()

    PreferenceTemplate(
        title = { Text(text = label) },
        endWidget = { ColorDot(color = MaterialTheme.colors.primary) },
        modifier = Modifier.clickable {
            coroutineScope.launch {
                bottomSheetState.show()
            }
        },
        description = {
            if (description != null) {
                Text(text = description)
            }
        }
    )

    BottomSheet(sheetState = bottomSheetState) {
        AlertBottomSheetContent(
            title = { Text(text = label) },
            buttons = {
                Button(
                    shape = MaterialTheme.shapes.small,
                    onClick = { coroutineScope.launch { bottomSheetState.hide() } }
                ) {
                    Text(text = stringResource(id = R.string.done))
                }
            }
        ) {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Chip(
                        label = stringResource(id = R.string.dynamic),
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 }
                    )
                    Chip(
                        label = stringResource(id = R.string.presets),
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 }
                    )
                }
                when (selectedTabIndex) {
                    0 -> {
                        DividerColumn(modifier = Modifier.padding(top = 16.dp)) {
                            dynamicEntries.map { entry ->
                                key(entry) {
                                    PreferenceTemplate(
                                        title = { Text(text = entry.label()) },
                                        verticalPadding = 12.dp,
                                        modifier = Modifier.clickable { selectedColor = entry.value },
                                        startWidget = {
                                            RadioButton(
                                                selected = entry.value == selectedColor,
                                                onClick = null
                                            )
                                            ColorDot(
                                                entry = entry,
                                                modifier = Modifier.padding(start = 16.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        SwatchGrid(
                            entries = staticEntries,
                            modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 16.dp),
                            onSwatchClick = { selectedColor = it },
                            isSwatchSelected = { it == selectedColor }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun <T> SwatchGrid(
    entries: List<ColorPreferenceEntry<T>>,
    onSwatchClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    isSwatchSelected: (T) -> Boolean
) {
    val columnCount = 6
    val rowCount = (entries.size.toDouble() / 6.0).toInt()
    val gutter = 12.dp

    Column(modifier = modifier) {
        for (rowNo in 1..rowCount) {
            val firstIndex = (rowNo - 1) * columnCount
            val lastIndex = firstIndex + columnCount - 1
            val indices = firstIndex..lastIndex

            Row {
                entries.slice(indices).forEachIndexed { index, colorOption ->
                    ColorSwatch(
                        entry = colorOption,
                        onClick = { onSwatchClick(colorOption.value) },
                        modifier = Modifier.weight(1F),
                        selected = isSwatchSelected(colorOption.value)
                    )
                    if (index != columnCount - 1) {
                        Spacer(modifier = Modifier.width(gutter))
                    }
                }
            }
            if (rowNo != rowCount) {
                Spacer(modifier = Modifier.height(gutter))
            }
        }
    }
}

@Composable
fun <T> ColorSwatch(
    entry: ColorPreferenceEntry<T>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    selected: Boolean
) {
    val color = if (MaterialTheme.colors.isLight) {
        entry.lightColor()
    } else {
        entry.darkColor()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio = 1F)
            .clip(CircleShape)
            .background(Color(color))
            .clickable(onClick = onClick)
    ) {
        Crossfade(targetState = selected) {
            if (it) {
                Icon(
                    imageVector = Icons.Rounded.Done,
                    contentDescription = null,
                    tint = MaterialTheme.colors.onPrimary
                )
            }
        }
    }
}

@Composable
fun <T> ColorDot(
    entry: ColorPreferenceEntry<T>,
    modifier: Modifier = Modifier
) {
    val color = if (MaterialTheme.colors.isLight) {
        entry.lightColor()
    } else {
        entry.darkColor()
    }

    ColorDot(
        color = Color(color),
        modifier = modifier
    )
}

@Composable
fun ColorDot(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color = color)
    )
}

@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colors.primary.copy(alpha = 0.08F)
        } else {
            Color.Transparent
        }
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colors.primary
        } else {
            MaterialTheme.colors.onBackground.copy(alpha = ContentAlpha.medium)
        }
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colors.primary
        } else {
            MaterialTheme.colors.onBackground.copy(alpha = 0.12F)
        }
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(32.dp)
            .clip(CircleShape)
            .border(width = 1.dp, color = borderColor, shape = CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            color = textColor
        )
    }
}

open class ColorPreferenceEntry<T>(
    val value: T,
    val label: @Composable () -> String,
    val lightColor: @Composable () -> Int,
    val darkColor: @Composable () -> Int = { lightenColor(lightColor()) },
)