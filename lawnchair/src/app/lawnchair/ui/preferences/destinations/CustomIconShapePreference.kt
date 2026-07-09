package app.lawnchair.ui.preferences.destinations

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchair.icons.shape.IconCornerShape
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.components.controls.getSteps
import app.lawnchair.ui.preferences.components.controls.snapSliderValue
import app.lawnchair.ui.preferences.components.layout.BottomSpacer
import app.lawnchair.ui.preferences.components.layout.PreferenceDivider
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.util.LocalBottomSheetHandler
import app.lawnchair.util.copyToClipboard
import app.lawnchair.util.getClipboardContent
import com.android.launcher3.R
import kotlin.math.roundToInt
import kotlin.toString

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CustomIconShapePreference(
    modifier: Modifier = Modifier,
    currentTab: ShapeRoute = ShapeRoute.APP_SHAPE,
) {
    val preferenceManager2 = preferenceManager2()

    val customIconShapeAdapter = when (currentTab) {
        ShapeRoute.APP_SHAPE -> preferenceManager2.customIconShape.getAdapter()
        ShapeRoute.FOLDER_SHAPE -> preferenceManager2.customFolderShape.getAdapter()
    }

    val appliedIconShape = customIconShapeAdapter.state.value
    val selectedIconShape = remember(currentTab) {
        mutableStateOf(IconShape.CustomCornerBased(appliedIconShape ?: IconShape.Circle))
    }

    val selectedIconShapeApplied = remember {
        derivedStateOf {
            // Force recompose here instead of outside
            customIconShapeAdapter.state.value.toString() == selectedIconShape.value.toString()
        }
    }

    val label = when (currentTab) {
        ShapeRoute.APP_SHAPE -> stringResource(id = R.string.custom_icon_shape)
        ShapeRoute.FOLDER_SHAPE -> stringResource(id = R.string.custom_folder_shape)
    }

    PreferenceLayout(
        label = label,
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                Button(
                    enabled = !selectedIconShapeApplied.value,
                    onClick = {
                        customIconShapeAdapter.onChange(newValue = selectedIconShape.value)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 16.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(
                        text = if (appliedIconShape != null) {
                            stringResource(id = R.string.action_apply)
                        } else {
                            stringResource(id = R.string.action_create)
                        },
                    )
                }
                BottomSpacer()
            }
        },
    ) {
        IconShapePreview(
            modifier = Modifier.padding(top = 12.dp),
            iconShape = selectedIconShape.value,
        )

        IconShapeCornerPreferenceGroup(
            selectedIconShape = selectedIconShape.value,
            onSelectedIconShapeChange = { newIconShape ->
                selectedIconShape.value = newIconShape
            },
        )

        IconShapeClipboardPreferenceGroup(
            selectedIconShape = selectedIconShape.value,
            onSelectedIconShapeChange = { newIconShape ->
                selectedIconShape.value = newIconShape
            },
        )
    }
}

@Composable
private fun IconShapeCornerPreferenceGroup(
    selectedIconShape: IconShape.CustomCornerBased,
    modifier: Modifier = Modifier,
    onSelectedIconShapeChange: (IconShape.CustomCornerBased) -> Unit,
) {
    PreferenceGroup(
        modifier = modifier,
        heading = stringResource(id = R.string.color_sliders),
    ) {
        IconShapeCornerPreference(
            title = stringResource(id = R.string.custom_icon_shape_top_left),
            scale = selectedIconShape.topLeft.scale.x,
            onScaleChange = {
                onSelectedIconShapeChange(selectedIconShape.copy(topLeftScale = it))
            },
            cornerShape = selectedIconShape.topLeft.shape,
            onCornerShapeChange = {
                onSelectedIconShapeChange(selectedIconShape.copy(topLeftShape = it))
            },
        )
        IconShapeCornerPreference(
            title = stringResource(id = R.string.custom_icon_shape_top_right),
            scale = selectedIconShape.topRight.scale.x,
            onScaleChange = {
                onSelectedIconShapeChange(selectedIconShape.copy(topRightScale = it))
            },
            cornerShape = selectedIconShape.topRight.shape,
            onCornerShapeChange = {
                onSelectedIconShapeChange(selectedIconShape.copy(topRightShape = it))
            },
        )
        IconShapeCornerPreference(
            title = stringResource(id = R.string.custom_icon_shape_bottom_left),
            scale = selectedIconShape.bottomLeft.scale.x,
            onScaleChange = {
                onSelectedIconShapeChange(selectedIconShape.copy(bottomLeftScale = it))
            },
            cornerShape = selectedIconShape.bottomLeft.shape,
            onCornerShapeChange = {
                onSelectedIconShapeChange(selectedIconShape.copy(bottomLeftShape = it))
            },
        )
        IconShapeCornerPreference(
            title = stringResource(id = R.string.custom_icon_shape_bottom_right),
            scale = selectedIconShape.bottomRight.scale.x,
            onScaleChange = {
                onSelectedIconShapeChange(selectedIconShape.copy(bottomRightScale = it))
            },
            cornerShape = selectedIconShape.bottomRight.shape,
            onCornerShapeChange = {
                onSelectedIconShapeChange(selectedIconShape.copy(bottomRightShape = it))
            },
        )
    }
}

@Composable
private fun IconShapeClipboardPreferenceGroup(
    selectedIconShape: IconShape.CustomCornerBased,
    modifier: Modifier = Modifier,
    onSelectedIconShapeChange: (IconShape.CustomCornerBased) -> Unit,
) {
    val context = LocalContext.current
    val importErrorMessage = stringResource(id = R.string.icon_shape_clipboard_import_error)
    PreferenceGroup(
        modifier = modifier,
        heading = stringResource(id = R.string.clipboard),
    ) {
        ClipboardButton(
            imageVector = Icons.Rounded.ContentCopy,
            label = stringResource(id = R.string.export_to_clipboard),
        ) {
            copyToClipboard(
                context = context,
                text = selectedIconShape.toString(),
            )
        }
        ClipboardButton(
            imageVector = Icons.Rounded.ContentPaste,
            label = stringResource(id = R.string.import_from_clipboard),
        ) {
            getClipboardContent(context)?.let {
                IconShape.CustomCornerBased.fromStringOrNull(it)
            }?.let {
                onSelectedIconShapeChange(it)
            } ?: run {
                Toast.makeText(context, importErrorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClipboardButton(
    label: String,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    PreferenceTemplate(
        title = { Text(text = label) },
        modifier = modifier,
        enabled = enabled,
        description = description?.let { { Text(text = it) } },
        startWidget = {
            val tint = LocalContentColor.current
            val contentAlpha = if (enabled) tint.alpha else 0.38f
            val alpha by animateFloatAsState(targetValue = contentAlpha, label = "")
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = tint.copy(alpha = alpha),
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun IconShapeCornerPreference(
    title: String,
    scale: Float,
    cornerShape: IconCornerShape,
    onScaleChange: (Float) -> Unit,
    onCornerShapeChange: (IconCornerShape) -> Unit,
    modifier: Modifier = Modifier,
) {
    CornerSlider(
        modifier = modifier,
        label = title,
        value = scale,
        onValueChange = { newValue ->
            onScaleChange(newValue)
        },
        cornerShape = cornerShape,
        onCornerShapeChange = onCornerShapeChange,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CornerSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    cornerShape: IconCornerShape,
    onCornerShapeChange: (IconCornerShape) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomSheetHandler = LocalBottomSheetHandler.current
    val options = listOf<IconCornerShape>(
        IconCornerShape.arc,
        IconCornerShape.Squircle,
        IconCornerShape.Cut,
    )

    val step = 0.1f
    val valueRange = 0f..1f

    PreferenceTemplate(
        modifier = modifier,
        title = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = label)
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    val valueText = stringResource(
                        id = R.string.n_percent,
                        (snapSliderValue(valueRange.start, value, step) * 100).roundToInt(),
                    )
                    Text(text = valueText)
                }
            }
        },
        description = {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    steps = getSteps(valueRange, step),
                    modifier = Modifier
                        .height(24.dp)
                        .weight(1f)
                        .padding(bottom = 8.dp),
                )
            }
        },
        endWidget = {
            Row(
                modifier = Modifier
                    .clip(shape = MaterialTheme.shapes.small)
                    .padding(top = 2.dp)
                    .clickable {
                        bottomSheetHandler.show {
                            ModalBottomSheetContent(
                                title = { Text(stringResource(id = R.string.custom_icon_shape_corner)) },
                                buttons = {
                                    OutlinedButton(
                                        onClick = { bottomSheetHandler.hide() },
                                        shapes = ButtonDefaults.shapes(),
                                    ) {
                                        Text(text = stringResource(id = android.R.string.cancel))
                                    }
                                },
                            ) {
                                LazyColumn {
                                    itemsIndexed(options) { index, option ->
                                        if (index > 0) {
                                            PreferenceDivider(startIndent = 40.dp)
                                        }
                                        val selected = cornerShape::class.java == option::class.java
                                        PreferenceTemplate(
                                            title = {
                                                Text(
                                                    text = option.getLabel(),
                                                )
                                            },
                                            startWidget = {
                                                RadioButton(
                                                    selected = selected,
                                                    onClick = null,
                                                )
                                            },
                                            onClick = {
                                                bottomSheetHandler.hide()
                                                onCornerShapeChange(option)
                                            },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    .padding(
                        vertical = 4.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.requiredWidthIn(min = 48.dp),
                    text = cornerShape.getLabel(),
                    fontSize = 14.sp,
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                )
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun CornerSliderPreview() {
    LawnchairTheme {
        CornerSlider(
            label = "Top Left",
            value = 0.5f,
            onValueChange = {},
            cornerShape = IconCornerShape.Squircle,
            onCornerShapeChange = {},
        )
    }
}

@Composable
private fun IconCornerShape.getLabel() = when (this) {
    IconCornerShape.Squircle -> stringResource(id = R.string.custom_icon_shape_corner_squircle)
    IconCornerShape.Cut -> stringResource(id = R.string.custom_icon_shape_corner_cut)
    else -> stringResource(id = R.string.custom_icon_shape_corner_round)
}
