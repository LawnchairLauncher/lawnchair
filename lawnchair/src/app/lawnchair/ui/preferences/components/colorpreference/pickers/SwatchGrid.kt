package app.lawnchair.ui.preferences.components.colorpreference.pickers

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreferenceEntry
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.theme.isSelectedThemeDark
import com.android.launcher3.R

object SwatchGridDefaults {
    val GutterSize = 12.dp
    val SwatchMaxWidth = 56.dp
    const val COLUMN_COUNT = 6
}

@Composable
fun <T> SwatchGrid(
    entries: List<ColorPreferenceEntry<T>>,
    onSwatchClick: (T) -> Unit,
    isSwatchSelected: (T) -> Boolean,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
) {
    val columnCount = SwatchGridDefaults.COLUMN_COUNT
    val rowCount = (entries.size - 1) / columnCount + 1
    val gutter = SwatchGridDefaults.GutterSize

    PreferenceGroup(
        heading = stringResource(id = R.string.swatches),
        modifier = modifier,
    ) {
        Item {
            Column(modifier = contentModifier) {
                for (rowNo in 1..rowCount) {
                    val firstIndex = (rowNo - 1) * columnCount
                    val lastIndex = firstIndex + columnCount - 1
                    val indices = firstIndex..lastIndex

                    Row {
                        entries.slice(indices).forEachIndexed { index, colorOption ->
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                ColorSwatch(
                                    entry = colorOption,
                                    onClick = { onSwatchClick(colorOption.value) },
                                    modifier = Modifier.widthIn(0.dp, SwatchGridDefaults.SwatchMaxWidth),
                                    selected = isSwatchSelected(colorOption.value),
                                )
                            }
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
    }
}

@Composable
fun <T> ColorSwatch(
    entry: ColorPreferenceEntry<T>,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (isSelectedThemeDark) {
        entry.darkColor(LocalContext.current)
    } else {
        entry.lightColor(LocalContext.current)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio = 1F)
            .clip(CircleShape)
            .background(Color(color))
            .clickable(onClick = onClick),
    ) {
        Crossfade(targetState = selected, label = "") {
            if (it) {
                Icon(
                    imageVector = Icons.Rounded.Done,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
