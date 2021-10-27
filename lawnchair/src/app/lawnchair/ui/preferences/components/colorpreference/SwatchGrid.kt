package app.lawnchair.ui.preferences.components.colorpreference

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object SwatchGridDefaults {
    val GutterSize = 12.dp
    const val ColumnCount = 6
}

@Composable
fun <T> SwatchGrid(
    entries: List<ColorPreferenceEntry<T>>,
    onSwatchClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    isSwatchSelected: (T) -> Boolean
) {
    val columnCount = SwatchGridDefaults.ColumnCount
    val rowCount = (entries.size - 1) / columnCount + 1
    val gutter = SwatchGridDefaults.GutterSize

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
