package app.lawnchair.ui.preferences.components.colorpreference

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
