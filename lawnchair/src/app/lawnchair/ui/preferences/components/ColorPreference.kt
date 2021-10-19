package app.lawnchair.ui.preferences.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.theme.lightenColor
import app.lawnchair.util.BackHandler
import com.android.launcher3.R
import com.google.accompanist.insets.navigationBarsPadding
import kotlinx.coroutines.launch

@Composable
@ExperimentalMaterialApi
@ExperimentalAnimationApi
fun <T> ColorPreference(
    previewColor: Color,
    colorAdapter: PreferenceAdapter<T>,
    lastCustomColorAdapter: PreferenceAdapter<T>,
    label: String,
    options: List<ColorPreferenceOption<T>>,
    customOptions: List<ColorPreferenceOption<T>>,
    showDivider: Boolean = false
) {
    val bottomSheetState = rememberBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()
    var selectedColor by colorAdapter
    var lastCustomColor by lastCustomColorAdapter

    PreferenceTemplate(
        title = { Text(text = label) },
        modifier = Modifier
            .clickable { coroutineScope.launch { bottomSheetState.show() } },
        endWidget = {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(previewColor)
            )
        },
        showDivider = showDivider,
        verticalPadding = 12.dp
    )

    BottomSheet(sheetState = bottomSheetState) {
        var selectingCustomColor by remember { mutableStateOf(value = false) }

        Column(modifier = Modifier.navigationBarsPadding()) {
            TopBar(
                label = if (selectingCustomColor) stringResource(id = R.string.custom_color) else label,
                showBackArrow = selectingCustomColor,
                onBackArrowClick = { selectingCustomColor = false }
            )
            if (selectingCustomColor) {
                BackHandler {
                    selectingCustomColor = false
                }
                ColorSwatchGrid(
                    options = customOptions,
                    onColorSwatchClicked = {
                        selectedColor = it
                        lastCustomColor = it
                    },
                    selectedColor = lastCustomColor,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )
            } else {
                options.mapIndexed { index, option ->
                    key(option) {
                        ModeRow(
                            onClick = { selectedColor = option.value },
                            selected = selectedColor == option.value,
                            option = option,
                            showDivider = index != 0
                        )
                    }
                }
                val customColorOption = customOptions.firstOrNull { it.value == selectedColor }
                val lastCustomColorOption = customOptions.first { it.value == lastCustomColor }
                ModeRow(
                    onClick = { selectedColor = lastCustomColor },
                    selected = customColorOption != null,
                    option = lastCustomColorOption,
                    onEditClick = { selectingCustomColor = true }
                )
                Spacer(modifier = Modifier.requiredHeight(16.dp))
            }
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 16.dp, bottom = 16.dp)
            ) {
                Button(
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary,
                        contentColor = MaterialTheme.colors.onPrimary
                    ),
                    onClick = {
                        coroutineScope.launch {
                            bottomSheetState.hide()
                        }
                    }
                ) {
                    Text(text = stringResource(id = R.string.done))
                }
            }
        }
    }
}

@Composable
@ExperimentalAnimationApi
fun <T> ColorSwatchGrid(
    options: List<ColorPreferenceOption<T>>,
    onColorSwatchClicked: (T) -> Unit,
    selectedColor: T,
    modifier: Modifier = Modifier
) {
    val columnCount = 6
    val rowCount = (options.size.toDouble() / 6.0).toInt()

    Column(modifier = modifier) {
        for (rowNo in 1..rowCount) {
            val firstIndex = (rowNo - 1) * columnCount
            val lastIndex = firstIndex + columnCount - 1
            val indices = firstIndex..lastIndex

            Row(modifier = Modifier.padding(horizontal = 13.dp)) {
                options.slice(indices).forEachIndexed { index, entry ->
                    ColorSwatch(
                        option = entry,
                        onClick = { onColorSwatchClicked(entry.value) },
                        modifier = Modifier.weight(1F),
                        isSelected = entry.value == selectedColor,
                        ringColor = MaterialTheme.colors.surface,
                        elevation = ModalBottomSheetDefaults.Elevation
                    )
                    if (index != columnCount - 1) {
                        Spacer(modifier = Modifier.requiredWidth(8.dp))
                    }
                }
            }

            if (rowNo != rowCount) {
                Spacer(modifier = Modifier.requiredHeight(8.dp))
            }
        }
    }
}

@Composable
@ExperimentalAnimationApi
fun TopBar(
    label: String,
    showBackArrow: Boolean,
    onBackArrowClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(56.dp)
            .padding(horizontal = 8.dp)
    ) {
        AnimatedVisibility(visible = showBackArrow) {
            ClickableIcon(
                imageVector = backIcon(),
                onClick = onBackArrowClick,
                tint = MaterialTheme.colors.onSurface
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
@ExperimentalAnimationApi
fun <T> ModeRow(
    onClick: () -> Unit,
    onEditClick: (() -> Unit)? = null,
    option: ColorPreferenceOption<T>,
    showDivider: Boolean = true,
    selected: Boolean
) {
    PreferenceTemplate(
        title = { Text(text = option.label()) },
        modifier = Modifier
            .clickable(onClick = onClick),
        startWidget = {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    unselectedColor = MaterialTheme.colors.onSurface.copy(alpha = 0.48F)
                )
            )
            Spacer(modifier = Modifier.requiredWidth(16.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(if (MaterialTheme.colors.isLight) option.lightColor() else option.darkColor()))
            )
        },
        endWidget = {
            if (onEditClick != null) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colors.primary
                ) {
                    ClickableIcon(
                        painter = painterResource(id = R.drawable.ic_edit),
                        onClick = onEditClick,
                        enabled = selected
                    )
                }
            } else {
                Spacer(modifier = Modifier.requiredHeight(40.dp))
            }
        },
        showDivider = showDivider,
        verticalPadding = 8.dp
    )
}

@Composable
@ExperimentalAnimationApi
fun <T> ColorSwatch(
    option: ColorPreferenceOption<T>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    ringColor: Color,
    elevation: Dp
) {
    val swatchPadding by animateDpAsState(targetValue = if (isSelected) 2.dp else 3.dp)
    val ringPadding by animateDpAsState(targetValue = if (isSelected) 5.dp else 0.dp)
    val darkTheme = !MaterialTheme.colors.isLight
    val color = if (darkTheme) option.darkColor() else option.lightColor()
    val elevationOverlay = LocalElevationOverlay.current
    val absoluteElevation = LocalAbsoluteElevation.current + elevation
    val ringColorWithOverlay = if (ringColor == MaterialTheme.colors.surface && elevationOverlay != null) {
        elevationOverlay.apply(ringColor, absoluteElevation)
    } else {
        ringColor
    }

    Box(
        modifier = modifier
            .aspectRatio(1F)
            .height(IntrinsicSize.Min)
            .width(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .padding(swatchPadding)
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color(color))
                .clickable(onClick = onClick)
        )
        Crossfade(targetState = isSelected) {
            if (it) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(ringPadding)
                        .border(3.dp, ringColorWithOverlay, CircleShape)
                )
            }
        }
    }
}

open class ColorPreferenceOption<T>(
    val value: T,
    val label: @Composable () -> String,
    val lightColor: @Composable () -> Int,
    val darkColor: @Composable () -> Int = { lightenColor(lightColor()) },
)
