package app.lawnchair.ui.preferences

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
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.RadioButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraphBuilder
import app.lawnchair.icons.shape.IconCornerShape
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.AlertBottomSheetContent
import app.lawnchair.ui.preferences.components.BottomSpacer
import app.lawnchair.ui.preferences.components.IconShapePreview
import app.lawnchair.ui.preferences.components.PreferenceDivider
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.PreferenceTemplate
import app.lawnchair.ui.preferences.components.getSteps
import app.lawnchair.ui.preferences.components.snapSliderValue
import app.lawnchair.ui.util.LocalBottomSheetHandler
import app.lawnchair.util.copyToClipboard
import app.lawnchair.util.getClipboardContent
import com.android.launcher3.R
import kotlin.math.roundToInt

fun NavGraphBuilder.customIconShapePreferenceGraph(route: String) {
    preferenceGraph(route, { CustomIconShapePreference() })
}

@Composable
private fun CustomIconShapePreference() {

    val preferenceManager2 = preferenceManager2()

    val customIconShapeAdapter = preferenceManager2.customIconShape.getAdapter()

    val appliedIconShape = customIconShapeAdapter.state.value
    val selectedIconShape = remember { mutableStateOf(appliedIconShape ?: IconShape.Circle) }
    val selectedIconShapeApplied = derivedStateOf {
        appliedIconShape.toString() == selectedIconShape.value.toString()
    }

    PreferenceLayout(
        label = stringResource(id = R.string.custom_icon_shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Button(
                    enabled = !selectedIconShapeApplied.value,
                    onClick = {
                        customIconShapeAdapter.onChange(newValue = selectedIconShape.value)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 16.dp),
                ) {
                    Text(
                        text = if (appliedIconShape != null) stringResource(id = R.string.apply_grid)
                        else stringResource(id = R.string.create),
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
            }
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
    selectedIconShape: IconShape,
    onSelectedIconShapeChange: (IconShape) -> Unit,
) {
    PreferenceGroup(
        heading = stringResource(id = R.string.color_sliders),
    ) {
        IconShapeCornerPreference(
            title = stringResource(id = R.string.icon_shape_top_left),
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
            title = stringResource(id = R.string.icon_shape_top_right),
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
            title = stringResource(id = R.string.icon_shape_bottom_left),
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
            title = stringResource(id = R.string.icon_shape_bottom_right),
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
    selectedIconShape: IconShape,
    onSelectedIconShapeChange: (IconShape) -> Unit,
) {
    val context = LocalContext.current
    val importErrorMessage = stringResource(id = R.string.icon_shape_clipboard_import_error)
    PreferenceGroup(
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
                IconShape.fromString(value = it)
            }?.let {
                onSelectedIconShapeChange(it)
            } ?: run {
                Toast.makeText(context, importErrorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun ClipboardButton(
    modifier: Modifier = Modifier,
    label: String,
    description: String? = null,
    enabled: Boolean = true,
    imageVector: ImageVector,
    onClick: () -> Unit,
) {
    PreferenceTemplate(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        contentModifier = Modifier,
        title = { Text(text = label) },
        description = { description?.let { Text(text = it) } },
        startWidget = {
            val tint = LocalContentColor.current
            val contentAlpha = if (enabled) tint.alpha else ContentAlpha.disabled
            val alpha by animateFloatAsState(targetValue = contentAlpha)
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = tint.copy(alpha = alpha)
            )
        },
        enabled = enabled,
    )
}

@Composable
private fun IconShapeCornerPreference(
    modifier: Modifier = Modifier,
    title: String,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    cornerShape: IconCornerShape,
    onCornerShapeChange: (IconCornerShape) -> Unit,
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

@Composable
private fun CornerSlider(
    modifier: Modifier = Modifier,
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    cornerShape: IconCornerShape,
    onCornerShapeChange: (IconCornerShape) -> Unit,
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
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
        title = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(text = label)
                CompositionLocalProvider(
                    LocalContentAlpha provides ContentAlpha.medium,
                    LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                ) {
                    val valueText = stringResource(
                        id = R.string.n_percent,
                        (snapSliderValue(valueRange.start, value, step) * 100).roundToInt()
                    )
                    Text(text = valueText)
                }
            }
        },
        description = {
            Row(
                modifier = Modifier.padding(top = 8.dp),
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
                        .padding(horizontal = 3.dp),
                )
            }
        },
        endWidget = {
            Row(
                modifier = Modifier
                    .padding(
                        start = 16.dp,
                        top = 12.dp,
                    )
                    .clip(shape = MaterialTheme.shapes.small)
                    .clickable {
                        bottomSheetHandler.show {
                            AlertBottomSheetContent(
                                title = { Text(stringResource(id = R.string.icon_shape_corner)) },
                                buttons = {
                                    OutlinedButton(onClick = { bottomSheetHandler.hide() }) {
                                        Text(text = stringResource(id = android.R.string.cancel))
                                    }
                                }
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
                                            modifier = Modifier.clickable {
                                                bottomSheetHandler.hide()
                                                onCornerShapeChange(option)
                                            },
                                            startWidget = {
                                                RadioButton(
                                                    selected = selected,
                                                    onClick = null
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    .padding(
                        start = 8.dp,
                        top = 4.dp,
                        bottom = 4.dp,
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
        applyPaddings = false,
    )
}

@Composable
private fun IconCornerShape.getLabel() = when (this) {
    IconCornerShape.Squircle -> stringResource(id = R.string.icon_shape_corner_squircle)
    IconCornerShape.Cut -> stringResource(id = R.string.icon_shape_corner_cut)
    else -> stringResource(id = R.string.icon_shape_corner_round)
}
