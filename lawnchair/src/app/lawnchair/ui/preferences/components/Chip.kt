package app.lawnchair.ui.preferences.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colors.primary.copy(alpha = 0.08F)
        } else {
            Color.Transparent
        }
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colors.primary
        } else {
            MaterialTheme.colors.onBackground.copy(alpha = ContentAlpha.medium)
        }
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colors.primary
        } else {
            MaterialTheme.colors.onBackground.copy(alpha = 0.12F)
        }
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(32.dp)
            .clip(CircleShape)
            .border(width = 1.dp, color = borderColor, shape = CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.body2,
            color = textColor
        )
    }
}
