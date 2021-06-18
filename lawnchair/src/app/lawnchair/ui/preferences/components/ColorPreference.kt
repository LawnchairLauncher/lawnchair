package app.lawnchair.ui.preferences.components

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.ui.theme.lightenColor
import app.lawnchair.ui.util.addIf
import com.android.launcher3.R
import com.google.accompanist.insets.navigationBarsPadding
import kotlinx.coroutines.launch

@Composable
@ExperimentalMaterialApi
@ExperimentalAnimationApi
fun <T> ColorPreference(
    adapter: PreferenceAdapter<T>,
    label: String,
    entries: List<ColorPreferenceEntry<T>>,
    showDivider: Boolean
) {
    val sheetState = rememberBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    val scope = rememberCoroutineScope()
    var currentColor by adapter

    fun applyColor(newColor: T) {
        currentColor = newColor
    }

    PreferenceTemplate(height = 52.dp, showDivider = showDivider) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .clickable { scope.launch { sheetState.show() } }
                .padding(start = 16.dp, end = 16.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.onBackground
            )
        }
    }

    BottomSheet(sheetState = sheetState) {
        var newColor by remember { mutableStateOf(currentColor) }

        Column(modifier = Modifier.navigationBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(56.dp)
                    .padding(start = 8.dp, end = 16.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(40.dp)
                        .height(40.dp)
                        .clip(CircleShape)
                        .clickable { scope.launch { sheetState.hide() } }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colors.onSurface
                    )
                }
                Spacer(modifier = Modifier.requiredWidth(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.h6
                )
            }
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                entries.forEachIndexed { index, it ->
                    ColorSwatch(
                        entry = it,
                        onClick = { newColor = it.value },
                        modifier = Modifier.weight(1F),
                        isSelected = it.value == newColor
                    )
                    if (index != entries.lastIndex) Spacer(modifier = Modifier.requiredWidth(12.dp))
                }
            }
            Button(
                shape = MaterialTheme.shapes.small,
                onClick = {
                    applyColor(newColor)
                    scope.launch {
                        sheetState.hide()
                    }
                },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(16.dp)
            ) {
                Text(text = stringResource(id = R.string.done))
            }
        }
    }
}

@Composable
@ExperimentalAnimationApi
fun <T> ColorSwatch(
    entry: ColorPreferenceEntry<T>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean
) {
    val lightColor = Color(entry.lightColor())
    val darkColor = Color(entry.darkColor())
    Box(
        modifier = modifier
            .aspectRatio(1F)
            .clip(CircleShape)
            .addIf(lightColor == darkColor) { background(lightColor) }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (lightColor != darkColor) {
            Image(painter = ColorSwatchPainter(lightColor, darkColor), contentDescription = "")
        }
        AnimatedCheck(
            visible = isSelected,
            tint = Color.White
        )
    }
}

class ColorSwatchPainter(private val lightColor: Color, private val darkColor: Color) : Painter() {

    override fun DrawScope.onDraw() {
        val size = drawContext.size
        rotate(45f) {
            drawRect(lightColor, size = Size(size.width / 2, size.height))
            drawRect(darkColor, topLeft = Offset(size.width / 2, 0f))
        }
    }

    override val intrinsicSize = Size.Unspecified
}

open class ColorPreferenceEntry<T>(
    val value: T,
    val lightColor: @Composable () -> Int,
    val darkColor: @Composable () -> Int = { lightenColor(lightColor()) },
)
