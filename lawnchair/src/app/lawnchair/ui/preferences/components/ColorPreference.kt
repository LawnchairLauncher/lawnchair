package app.lawnchair.ui.preferences.components

import androidx.annotation.ColorInt
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.theme.getSystemAccent
import app.lawnchair.ui.theme.lightenColor
import app.lawnchair.util.backHandler
import com.android.launcher3.R
import com.google.accompanist.insets.navigationBarsPadding
import kotlinx.coroutines.launch

@Composable
@ExperimentalMaterialApi
@ExperimentalAnimationApi
fun ColorPreference(
    @ColorInt previewColor: Int,
    customColorAdapter: PreferenceAdapter<Int>,
    label: String,
    presets: List<ColorPreferencePreset>,
    showDivider: Boolean,
    useSystemAccentAdapter: PreferenceAdapter<Boolean>
) {
    val bottomSheetState = rememberBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()
    var customColor by customColorAdapter
    var useSystemAccent by useSystemAccentAdapter
    val context = LocalContext.current

    PreferenceTemplate(
        height = 52.dp,
        showDivider = showDivider
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    coroutineScope.launch {
                        bottomSheetState.show()
                    }
                }
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onBackground
            )
            Box(
                modifier = Modifier
                    .size(width = 24.dp, height = 24.dp)
                    .clip(CircleShape)
                    .background(Color(previewColor))
            )
        }
    }
    BottomSheet(sheetState = bottomSheetState) {
        var selectingCustomColor by remember { mutableStateOf(value = false) }

        Column(modifier = Modifier.navigationBarsPadding()) {
            TopBar(
                label = if (selectingCustomColor) stringResource(id = R.string.custom_color) else label,
                showBackArrow = selectingCustomColor,
                onBackArrowClick = { selectingCustomColor = false }
            )
            if (selectingCustomColor) {
                backHandler {
                    selectingCustomColor = false
                }
                ColorSwatchGrid(
                    presets = presets,
                    onColorSwatchClicked = { customColor = it },
                    customColor = customColor,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )
            } else {
                ModeRow(
                    label = stringResource(id = R.string.system),
                    onClick = { useSystemAccent = true },
                    selected = useSystemAccent,
                    lightThemeColor = context.getSystemAccent(darkTheme = false),
                    darkThemeColor = context.getSystemAccent(darkTheme = true)
                )
                ModeRow(
                    label = stringResource(id = R.string.custom),
                    onClick = { useSystemAccent = false },
                    selected = !useSystemAccent,
                    lightThemeColor = customColor,
                    darkThemeColor = lightenColor(customColor),
                    showDivider = false,
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
fun ColorSwatchGrid(
    presets: List<ColorPreferencePreset>,
    onColorSwatchClicked: (Int) -> Unit,
    customColor: Int,
    modifier: Modifier = Modifier
) {
    val columnCount = 6
    val rowCount = (presets.size.toDouble() / 6.0).toInt()

    Column(modifier = modifier) {
        for (rowNo in 1..rowCount) {
            val firstIndex = (rowNo - 1) * columnCount
            val lastIndex = firstIndex + columnCount - 1
            val indices = firstIndex..lastIndex

            Row(modifier = Modifier.padding(horizontal = 13.dp)) {
                presets.slice(indices).forEachIndexed { index, entry ->
                    ColorSwatch(
                        preset = entry,
                        onClick = { onColorSwatchClicked(entry.value) },
                        modifier = Modifier.weight(1F),
                        isSelected = entry.value == customColor
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
        modifier = Modifier.height(56.dp)
    ) {
        AnimatedVisibility(visible = showBackArrow) {
            ClickableIcon(
                painter = painterResource(id = R.drawable.ic_back),
                onClick = onBackArrowClick,
                tint = MaterialTheme.colors.onSurface
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(
                start = animateDpAsState(targetValue = if (showBackArrow) 8.dp else 16.dp).value
            )
        )
    }
}

@Composable
@ExperimentalAnimationApi
fun ModeRow(
    label: String,
    onClick: () -> Unit,
    onEditClick: (() -> Unit)? = null,
    @ColorInt lightThemeColor: Int,
    @ColorInt darkThemeColor: Int,
    showDivider: Boolean = true,
    selected: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(52.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp)
    ) {
        AnimatedCheck(visible = selected)
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .weight(1F)
                    .fillMaxWidth()
            ) {
                Row(modifier = Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp, 24.dp)
                            .clip(CircleShape)
                            .background(Color(if (MaterialTheme.colors.isLight) lightThemeColor else darkThemeColor))
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                if (onEditClick != null) {
                    ClickableIcon(
                        painter = painterResource(id = R.drawable.ic_edit),
                        tint = MaterialTheme.colors.primary,
                        onClick = onEditClick
                    )
                }
            }
            if (showDivider) {
                Divider(modifier = Modifier.padding(end = 8.dp))
            }
        }
    }
}

@Composable
@ExperimentalAnimationApi
fun ColorSwatch(
    preset: ColorPreferencePreset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean
) {
    val swatchPadding by animateDpAsState(targetValue = if (isSelected) 2.dp else 3.dp)
    val ringPadding by animateDpAsState(targetValue = if (isSelected) 5.dp else 0.dp)
    val darkTheme = !MaterialTheme.colors.isLight

    Box(
        modifier = modifier
            .aspectRatio(1F)
            .height(IntrinsicSize.Min)
            .width(IntrinsicSize.Min)
    ) {
        Box(
            modifier = modifier
                .padding(swatchPadding)
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color(if (darkTheme) preset.darkColor() else preset.lightColor()))
                .clickable(onClick = onClick)
        )
        Crossfade(targetState = isSelected) {
            if (it) {
                Box(
                    modifier = Modifier
                        .padding(ringPadding)
                        .fillMaxSize()
                        .border(3.dp, MaterialTheme.colors.surface, CircleShape)
                )
            }
        }
    }
}

open class ColorPreferencePreset(
    val value: Int,
    val lightColor: @Composable () -> Int,
    val darkColor: @Composable () -> Int = { lightenColor(lightColor()) },
)
