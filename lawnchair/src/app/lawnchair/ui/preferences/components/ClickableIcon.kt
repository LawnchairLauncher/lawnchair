package app.lawnchair.ui.preferences.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter

@Composable
fun ClickableIcon(
    painter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        val contentAlpha = if (enabled) tint.alpha else ContentAlpha.disabled
        val alpha by animateFloatAsState(targetValue = contentAlpha, label = "")
        Icon(
            painter = painter,
            contentDescription = null,
            tint = tint.copy(alpha = alpha)
        )
    }
}

@Composable
fun ClickableIcon(
    imageVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current
) {
    ClickableIcon(
        painter = rememberVectorPainter(image = imageVector),
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        tint = tint
    )
}
