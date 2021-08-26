package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.util.addIf

@Composable
fun ClickableIcon(
    painter: Painter,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(height = 40.dp, width = 40.dp)
            .clip(CircleShape)
            .addIf(onClick != null) {
                clickable(onClick = onClick!!)
            }
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = tint
        )
    }
}

@Composable
fun ClickableIcon(
    imageVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
) {
    ClickableIcon(
        painter = rememberVectorPainter(image = imageVector),
        onClick = onClick,
        modifier = modifier,
        tint = tint
    )
}
