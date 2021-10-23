package app.lawnchair.ui.preferences

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
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.AlertBottomSheetContent
import app.lawnchair.ui.preferences.components.BottomSheet
import app.lawnchair.ui.preferences.components.DividerColumn
import app.lawnchair.ui.preferences.components.PreferenceTemplate
import app.lawnchair.ui.preferences.components.rememberBottomSheetState
import app.lawnchair.ui.theme.lightenColor
import com.android.launcher3.R
import kotlinx.coroutines.launch

val staticColors = listOf(
    ColorOption.CustomColor(0xFFF32020),
    ColorOption.CustomColor(0xFFF20D69),
    ColorOption.CustomColor(0xFF7452FF),
    ColorOption.CustomColor(0xFF2C41C9),
    ColorOption.LawnchairBlue,
    ColorOption.CustomColor(0xFF00BAD6),
    ColorOption.CustomColor(0xFF00A399),
    ColorOption.CustomColor(0xFF47B84F),
    ColorOption.CustomColor(0xFFFFBB00),
    ColorOption.CustomColor(0xFFFF9800),
    ColorOption.CustomColor(0xFF7C5445),
    ColorOption.CustomColor(0xFF67818E)
).map(ColorOption::accentColorOption)

val dynamicColors = listOf(ColorOption.SystemAccent, ColorOption.WallpaperPrimary)
    .filter(ColorOption::isSupported)
    .map(ColorOption::accentColorOption)

@Composable
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterialApi::class)
fun AccentColorPreferences() {
    val pm = preferenceManager()
    var accentColor by pm.accentColor.getAdapter()
    val defaultTabIndex = if (dynamicColors.any { accentColor == it.value }) 0 else 1
    var selectedTabIndex by remember { mutableStateOf(value = defaultTabIndex) }
    val bottomSheetState = rememberBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()
    val description = (dynamicColors + staticColors).firstOrNull { it.value == pm.accentColor.get() }?.label?.invoke()

    PreferenceTemplate(
        title = { Text(text = stringResource(id = R.string.accent_color)) },
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

    BottomSheet(
        sheetState = bottomSheetState,
        sheetBackgroundColor = MaterialTheme.colors.background
    ) {
        AlertBottomSheetContent(
            title = { Text(text = stringResource(id = R.string.accent_color)) },
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
                            dynamicColors.map { accentColorOption ->
                                key(accentColorOption) {
                                    PreferenceTemplate(
                                        title = { Text(text = accentColorOption.label()) },
                                        verticalPadding = 12.dp,
                                        modifier = Modifier.clickable { accentColor = accentColorOption.value },
                                        startWidget = {
                                            RadioButton(
                                                selected = accentColorOption.value == accentColor,
                                                onClick = null
                                            )
                                            ColorDot(
                                                accentColorOption = accentColorOption,
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
                            accentColorOptions = staticColors,
                            modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 16.dp),
                            onSwatchClick = { accentColor = it },
                            isSwatchSelected = { it == accentColor }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun <T> SwatchGrid(
    accentColorOptions: List<AccentColorOption<T>>,
    onSwatchClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    isSwatchSelected: (T) -> Boolean
) {
    val columnCount = 6
    val rowCount = (staticColors.size.toDouble() / 6.0).toInt()
    val gutter = 12.dp

    Column(modifier = modifier) {
        for (rowNo in 1..rowCount) {
            val firstIndex = (rowNo - 1) * columnCount
            val lastIndex = firstIndex + columnCount - 1
            val indices = firstIndex..lastIndex

            Row {
                accentColorOptions.slice(indices).forEachIndexed { index, colorOption ->
                    ColorSwatch(
                        accentColorOption = colorOption,
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
    accentColorOption: AccentColorOption<T>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    selected: Boolean
) {
    val color = if (MaterialTheme.colors.isLight) {
        accentColorOption.lightColor()
    } else {
        accentColorOption.darkColor()
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
    accentColorOption: AccentColorOption<T>,
    modifier: Modifier = Modifier
) {
    val color = if (MaterialTheme.colors.isLight) {
        accentColorOption.lightColor()
    } else {
        accentColorOption.darkColor()
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
            .height(36.dp)
            .clip(CircleShape)
            .border(width = 1.dp, color = borderColor, shape = CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            color = textColor
        )
    }
}

open class AccentColorOption<T>(
    val value: T,
    val label: @Composable () -> String,
    val lightColor: @Composable () -> Int,
    val darkColor: @Composable () -> Int = { lightenColor(lightColor()) },
)